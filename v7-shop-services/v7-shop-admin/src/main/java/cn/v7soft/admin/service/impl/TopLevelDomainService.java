package cn.v7soft.admin.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
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
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
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

    public TopLevelDomainService(TopLevelDomainRepository repository, ITopLevelDomainDao topLevelDomainDao,
                                 IFrontServerService frontServerService, PlaceholderCertHolder placeholderCertHolder) {
        super(repository);
        this.topLevelDomainDao = topLevelDomainDao;
        this.frontServerService = frontServerService;
        this.placeholderCertHolder = placeholderCertHolder;
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
        TopLevelDomain topLevelDomain = getById(id);
        List<SubDomain> subDomains = topLevelDomain.getSubDomains();
        subDomainService.doDeleteAll(subDomains.stream().map(SubDomain::getId).toList());
        cleanupDomainResources(topLevelDomain);
        topLevelDomain.setStatus(StatusEnum.DELETED);
        repository.save(topLevelDomain);
    }

    /**
     * 清理域名关联的本地证书文件和所有前端服务器上的nginx配置
     */
    private void cleanupDomainResources(TopLevelDomain domain) {
        String domainName = domain.getName();
        String companyId = String.valueOf(domain.getCompanyId());

        // 删除本地SSL证书文件
        String certDir = CERT_CONFIG_DIR + companyId + "/" + domainName;
        try {
            if (FileUtil.exist(certDir)) {
                FileUtil.del(certDir);
                log.info("已删除域名 {} 的本地证书目录: {}", domainName, certDir);
            }
        } catch (Exception e) {
            log.error("删除域名 {} 的证书目录失败: {}", domainName, certDir, e);
        }

        // 删除所有前端服务器上的nginx配置并刷新
        try {
            for (FrontServer frontServer : frontServerService.listFrontServers()) {
                String serverName = frontServer.getName();
                if (NginxConfigWriter.deleteNginxIfExists(serverName, domainName)) {
                    frontServerService.pushAndRefresh(frontServer.getId());
                    log.info("已删除域名 {} 在服务器 {} 上的nginx配置", domainName, serverName);
                }
            }
        } catch (Exception e) {
            log.error("清理域名 {} 的nginx配置失败", domainName, e);
        }
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
        TopLevelDomain domain = getById(request.getIdLongValue());

        // valid fullchain and privkey
        SslCertificateUtil.valid(domain, request.getFullChain(), request.getPrivateKey());
        LocalDateTime expiredDateTime = SslCertificateUtil.getExpiryDate(domain);

        final String targetDir = CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";
        String fullChainPemPath = targetDir + "fullchain.pem";
        String privkeyPemPath = targetDir + "privkey.pem";
        log.debug("fullChainPath = " + fullChainPemPath);
        log.debug("privkeyPemPath = " + privkeyPemPath);
        placeholderCertHolder.writePemPair(targetDir, request.getFullChain(), request.getPrivateKey());
        SSLCertificate sslCertificate = domain.getSslCertificate();
        if (sslCertificate == null) {
            sslCertificate = SSLCertificate.builder().build();
        }
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("sh /scripts/push.sh");
            String sslPushMsg = IoUtil.read(process.getInputStream(), Charsets.UTF_8);
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
        } catch (IOException e) {
            log.error("更新证书失败", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
        return GetCertificateResp.builder()
                .fullChain(request.getFullChain())
                .privateKey(request.getPrivateKey())
                .expiredDateTime(null)
                .build();
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
        try {
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
