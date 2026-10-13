package cn.v7soft.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.BindPixelsRequest;
import cn.v7soft.admin.controller.req.BindProtocolRequest;
import cn.v7soft.admin.controller.req.GetCertificateReq;
import cn.v7soft.admin.controller.req.TransferUserRequest;
import cn.v7soft.admin.controller.req.UpdateCertificateReq;
import cn.v7soft.admin.controller.resp.GetCertificateResp;
import cn.v7soft.admin.dao.ITopLevelDomainDao;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.admin.service.ssl.PlaceholderCertHolder;
import cn.v7soft.admin.utils.NginxConfigWriter;
import cn.v7soft.admin.utils.NginxWorkspace;
import cn.v7soft.admin.utils.NginxPublisher;
import cn.v7soft.admin.service.ssl.DomainResourceCleaner;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.entities.primary.Protocol;
import cn.v7soft.dao.entities.primary.SSLCertificate;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.CertificateRequestStatus;
import cn.v7soft.dao.enums.NginxConfigType;
import cn.v7soft.dao.repositories.primary.TopLevelDomainRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import kotlin.text.Charsets;
import lombok.extern.slf4j.Slf4j;

import static cn.v7soft.admin.service.ssl.ISslCertificateRequester.CERT_CONFIG_DIR;

@Slf4j
@Service
public class TopLevelDomainService extends BaseDataRangeService<TopLevelDomain, TopLevelDomainRepository> implements ITopLevelDomainService {


    private SubDomainService subDomainService;
    private final ITopLevelDomainDao topLevelDomainDao;
    private final IFrontServerService frontServerService;
    private final PlaceholderCertHolder placeholderCertHolder;
    private final DomainResourceCleaner resourceCleaner;

    public TopLevelDomainService(TopLevelDomainRepository repository, ITopLevelDomainDao topLevelDomainDao,
                                 IFrontServerService frontServerService, PlaceholderCertHolder placeholderCertHolder, DomainResourceCleaner resourceCleaner) {
        super(repository);
        this.topLevelDomainDao = topLevelDomainDao;
        this.frontServerService = frontServerService;
        this.placeholderCertHolder = placeholderCertHolder;
        this.resourceCleaner = resourceCleaner;
    }

    @Lazy
    @Autowired
    public void setSubDomainService(SubDomainService subDomainService) {
        this.subDomainService = subDomainService;
    }

    @Override
    protected void checkKeyConstraint(TopLevelDomain entity) {
        long sameNameCount = repository.countBySameName(entity.getName(), entity.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(sameNameCount == 0, "一级域名不允许重复");
    }

    @Override
    public List<TopLevelDomain> findAllQueueOrRequesting() {
        return repository.findAllQueueOrRequesting();
    }

    @Override
    @Transactional
    public void bindProtocol(BindProtocolRequest request) {
        TopLevelDomain topLevelDomain = getById(request.getIdLongValue());
        if (StrUtil.isBlank(request.getProtocolId())) {
            topLevelDomain.setProtocol(null);
            topLevelDomain.setPlaceholderValues(null);
        } else {
            topLevelDomain.setProtocol(Protocol.builder().id(Long.valueOf(request.getProtocolId())).build());
            topLevelDomain.setPlaceholderValues(request.getPlaceholderValues());
        }
        saveAndFlush(topLevelDomain);
    }

    @Override
    @Transactional
    public void transferUser(TransferUserRequest request) {
        TopLevelDomain topLevelDomain = getById(request.getIdLongValue());
        SystemUser owner = SystemUser.builder().id(Long.valueOf(request.getTransferUserId())).build();
        topLevelDomain.setOwner(owner);
        save(topLevelDomain);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        try (var ignored = NginxWorkspace.lock()) {
            TopLevelDomain topLevelDomain = getById(id);
            assertCanDelete(topLevelDomain);
            List<SubDomain> subDomains = topLevelDomain.getSubDomains();
            subDomainService.deleteChildrenForParent(subDomains);
            resourceCleaner.deleteDomain(topLevelDomain, frontServerService::pushAndRefreshAll);
            topLevelDomain.setStatus(StatusEnum.DELETED);
            repository.save(topLevelDomain);
        }
    }

    private void assertCanDelete(TopLevelDomain topLevelDomain) {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        if (loginUser.isAdmin()) {
            return;
        }
        SystemUser owner = topLevelDomain.getOwner();
        ClientResponseEnum.NO_PERMISSION.notNull(owner, "您无权限操作该域名");
        Long ownerDepartmentId = owner.getDepartment() == null ? null : owner.getDepartment().getId();
        boolean canDelete = loginUser.hasManagerPermission(owner.getId(), ownerDepartmentId);
        ClientResponseEnum.NO_PERMISSION.assertTrue(canDelete, "您无权限操作该域名");
    }

    @Override
    @Transactional
    public void deleteAll(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    @Transactional
    public GetCertificateResp getCertificate(GetCertificateReq request) {
        TopLevelDomain domain = getById(request.getIdLongValue());
        final String targetDir = CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";
        String fullChainPemPath = targetDir + "fullchain.pem";
        String privkeyPemPath = targetDir + "privkey.pem";
        LocalDateTime expiredDateTime = null;
        String fullChainPem = "";
        String privkeyPem = "";
        if (FileUtil.exist(fullChainPemPath)) {
            fullChainPem = FileUtil.readString(fullChainPemPath, Charsets.UTF_8);
        }
        if (FileUtil.exist(privkeyPemPath)) {
            privkeyPem = FileUtil.readString(privkeyPemPath, Charsets.UTF_8);
            expiredDateTime = SslCertificateUtil.getExpiryDate(domain);
        }

        return GetCertificateResp.builder()
                .fullChain(fullChainPem)
                .privateKey(privkeyPem)
                .expiredDateTime(expiredDateTime)
                .build();
    }

    @Override
    public GetCertificateResp updateCertificate(UpdateCertificateReq request) {
        try (var ignored = NginxWorkspace.lock()) {
            TopLevelDomain domain = getById(request.getIdLongValue());
            SslCertificateUtil.valid(domain, request.getFullChain(), request.getPrivateKey());
            String targetDir = CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";
            placeholderCertHolder.writePemPair(targetDir, request.getFullChain(), request.getPrivateKey());
            LocalDateTime expiredDateTime = SslCertificateUtil.getExpiryDate(domain);
            SSLCertificate sslCertificate = domain.getSslCertificate();
            if (sslCertificate == null) {
                sslCertificate = SSLCertificate.builder().build();
            }
            String sslPushMsg = NginxPublisher.push();
            sslCertificate.setCertificateExpiryDate(expiredDateTime);
            sslCertificate.setError(false);
            sslCertificate.setResult("手动部署成功");
            sslCertificate.setCompleted(true);
            sslCertificate.setSuccess(true);
            sslCertificate.setError(false);
            sslCertificate.setErrLog("");
            sslCertificate.setErrorMsg("");
            sslCertificate.setSslPushMsg(sslPushMsg);
            domain.setCertificateRequestStatus(CertificateRequestStatus.FINISH);
            domain.setSslCertificate(sslCertificate);
            topLevelDomainDao.saveAndFlush(domain);
            return GetCertificateResp.builder()
                    .fullChain(request.getFullChain())
                    .privateKey(request.getPrivateKey())
                    .expiredDateTime(expiredDateTime)
                    .build();
        }
    }

    @Override
    @Transactional
    public void nginxConfig(Long id, NginxConfigType type) {
        TopLevelDomain topLevelDomain = getById(id);
        topLevelDomain.setNginxConfigType(type);
        saveAndFlush(topLevelDomain);
    }

    @Override
    @Async("certificateRequestAsyncExecutor")
    public void refreshNginxConfig(Long id, NginxConfigType type) {
        try (var ignored = NginxWorkspace.lock()) {
            TopLevelDomain topLevelDomain = getById(id);
            String domain = topLevelDomain.getName();
            // 检查域名证书是否正常
            placeholderCertHolder.ensureWritten(topLevelDomain);
            SslCertificateUtil.valid(topLevelDomain);
            String companyId = String.valueOf(topLevelDomain.getCompanyId());
            for (FrontServer frontServer : frontServerService.listFrontServers()) {
                String frontServerName = frontServer.getName();
                if (NginxConfigWriter.existsNginxConfig(frontServerName, domain)) {
                    boolean writeNginx = NginxConfigWriter.writeNginx(frontServerName, domain, type, companyId);
                    if (writeNginx) {
                        frontServerService.pushAndRefresh(frontServer.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("刷新nginx配置失败", e);
        }
    }

    @Override
    @Transactional
    public void bindPixels(BindPixelsRequest request) {
        TopLevelDomain topLevelDomain = getById(request.getIdLongValue());
        if (request.getPixelIds() == null || request.getPixelIds().isEmpty()) {
            topLevelDomain.setPixelAccounts(null);
        } else {
            List<PixelAccount> pixelAccounts = request.getPixelIds().stream()
                    .map(id -> PixelAccount.builder().id(Long.valueOf(id)).build())
                    .collect(Collectors.toList());
            topLevelDomain.setPixelAccounts(pixelAccounts);
        }
        saveAndFlush(topLevelDomain);
    }

}
