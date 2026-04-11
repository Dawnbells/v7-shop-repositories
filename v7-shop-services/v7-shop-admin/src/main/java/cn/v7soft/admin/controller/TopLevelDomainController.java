package cn.v7soft.admin.controller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.BindPixelsRequest;
import cn.v7soft.admin.controller.req.BindProtocolRequest;
import cn.v7soft.admin.controller.req.EditTopLevelDomainRequest;
import cn.v7soft.admin.controller.req.GetCertificateReq;
import cn.v7soft.admin.controller.req.ParseCertificateReq;
import cn.v7soft.admin.controller.req.QueryTopLevelDomainRequest;
import cn.v7soft.admin.controller.req.TransferUserRequest;
import cn.v7soft.admin.controller.req.UpdateCertificateReq;
import cn.v7soft.admin.controller.resp.GetCertificateResp;
import cn.v7soft.admin.controller.resp.TopLevelDomainResponse;
import cn.v7soft.admin.events.CertificateRequestPublisher;
import cn.v7soft.admin.service.ICloudPlatformAccountService;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.common.dto.SSLCertificateInfo;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.CertificateRequestStatus;
import cn.v7soft.dao.enums.NginxConfigType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.Valid;

import static cn.v7soft.dao.enums.NginxConfigType.THYMELEAF;

@RestController
@RequestMapping("/top-level-domain")
@Tag(name = "证书管理-域名管理")
@Validated
public class TopLevelDomainController extends BaseDataRangeController<TopLevelDomain, ITopLevelDomainService, TopLevelDomainResponse, QueryTopLevelDomainRequest, EditTopLevelDomainRequest> {

    private final CertificateRequestPublisher certificateRequestPublisher;
    private final ICloudPlatformAccountService cloudPlatformAccountService;
    private final IDnsService dnsService;

    protected TopLevelDomainController(ITopLevelDomainService service, CertificateRequestPublisher certificateRequestPublisher,
                                       ICloudPlatformAccountService cloudPlatformAccountService, IDnsService dnsService) {
        super(service);
        this.certificateRequestPublisher = certificateRequestPublisher;
        this.cloudPlatformAccountService = cloudPlatformAccountService;
        this.dnsService = dnsService;
    }

    @Override
    protected QueryPageRequest<TopLevelDomain> convertQueryPageRequest(QueryTopLevelDomainRequest request) {
        String clientSortBy = request.getSortBy();
        boolean sortByCertExpiry = clientSortBy != null && clientSortBy.startsWith("certificateExpiryDate");
        boolean sortByExpiry = clientSortBy != null && clientSortBy.startsWith("expiryDate");

        if (sortByCertExpiry) {
            request.noneSortBy();
            boolean ascending = clientSortBy.endsWith("asc");
            return super.convertQueryPageRequest(request)
                    .addConstraint(StrUtil.isNotBlank(request.getTitle()),
                                   LikeAttribute.builder().name("name").value("%" + request.getTitle() + "%").build())
                    .add(new QueryAttribute() {
                        @Override
                        public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                            Path<LocalDateTime> expiryPath = root.get("sslCertificate").get("certificateExpiryDate");
                            if (ascending) {
                                query.orderBy(cb.asc(expiryPath), cb.desc(root.get("id")));
                            } else {
                                query.orderBy(cb.desc(expiryPath), cb.desc(root.get("id")));
                            }
                            return cb.conjunction();
                        }
                    });
        } else if (sortByExpiry) {
            boolean ascending = clientSortBy.endsWith("asc");
            request.setSortBy("expiryDate " + (ascending ? "asc" : "desc") + ", id desc");
            return super.convertQueryPageRequest(request)
                    .addConstraint(StrUtil.isNotBlank(request.getTitle()),
                                   LikeAttribute.builder().name("name").value("%" + request.getTitle() + "%").build());
        } else {
            request.setSortBy("id desc");
            return super.convertQueryPageRequest(request)
                    .addConstraint(StrUtil.isNotBlank(request.getTitle()),
                                   LikeAttribute.builder().name("name").value("%" + request.getTitle() + "%").build());
        }
    }

    @Override
    protected TopLevelDomainResponse convertEntity(TopLevelDomain entity) {
        return TopLevelDomainResponse.convertEntity(entity);
    }

    @Override
    protected TopLevelDomain convertRequest(@Nullable TopLevelDomain dbEntity, EditTopLevelDomainRequest request) {
        TopLevelDomain entity = Optional.ofNullable(dbEntity).orElse(TopLevelDomain.builder().build());
        BeanUtil.copyProperties(request, entity, "id", "type");
        CloudPlatformAccount cloudPlatformAccount = null;
        if (request.getCloudPlatformAccountId() != null) {
            cloudPlatformAccount = cloudPlatformAccountService.getById(request.getCloudPlatformAccountId());
            entity.setCloudPlatformAccount(cloudPlatformAccount);
        }
        if (dbEntity == null) {
            entity.setType(request.getType());
            entity.setCertificateRequestStatus(CertificateRequestStatus.IDLE);
        }
        entity.setNginxConfigType(THYMELEAF);
        // 自动获取域名过期时间，获取成功则覆盖用户设置，失败则使用用户设置
        if (cloudPlatformAccount != null && StrUtil.isNotBlank(request.getName())) {
            LocalDateTime expiryDate = dnsService.queryDomainExpiryDate(cloudPlatformAccount, request.getName());
            if (expiryDate != null) {
                entity.setExpiryDate(expiryDate);
            }
        }
        return entity;
    }

    @Override
    protected TopLevelDomain doEditOperate(EditTopLevelDomainRequest request) {
        TopLevelDomain topLevelDomain = super.doEditOperate(request);
        if (topLevelDomain.getCertificateRequestStatus() == CertificateRequestStatus.IDLE) {
            topLevelDomain.setCertificateRequestStatus(CertificateRequestStatus.QUEUE);
            service.saveAndFlush(topLevelDomain);
            certificateRequestPublisher.requestCertificate(topLevelDomain.getId());
        }
        return topLevelDomain;
    }

    @Override
    protected String getPermissionPrefix() {
        return "top-level-domain";
    }

    @PostMapping("/renew_certificate")
    @Operation(summary = "续期证书")
    @SaCheckPermission("top-level-domain.renew_certificate")
    public void renewCertificate(@Valid @RequestBody IdRequest request) {
        TopLevelDomain topLevelDomain = service.getById(request.getIdLongValue());
        topLevelDomain.setCertificateRequestStatus(CertificateRequestStatus.QUEUE);
        service.saveAndFlush(topLevelDomain);
        certificateRequestPublisher.requestCertificate(topLevelDomain.getId());
    }

    @Operation(summary = "更新证书")
    @PostMapping("/nginx-config/{id}/{type}")
    @SaCheckPermission("top-level-domain.nginx-config")
    public void nginxConfig(@PathVariable("id") Long id, @PathVariable("type") String type) {
        NginxConfigType nginxConfigType = "vike".equalsIgnoreCase(type) ? NginxConfigType.VIKE : THYMELEAF;
        service.nginxConfig(id, nginxConfigType);
        service.refreshNginxConfig(id, nginxConfigType);
    }

    @PostMapping("/transfer")
    @Operation(summary = "转移用户")
    @SaCheckPermission("top-level-domain.transfer")
    public void transferUser(@Valid @RequestBody TransferUserRequest request) {
        service.transferUser(request);
    }

    @PostMapping("/bindProtocol")
    @Operation(summary = "绑定协议")
    @SaCheckPermission("top-level-domain.bindProtocol")
    public void bindProtocol(@Valid @RequestBody BindProtocolRequest request) {
        service.bindProtocol(request);
    }

    @PostMapping("/bindPixels")
    @Operation(summary = "绑定像素账号")
    @SaCheckPermission("top-level-domain.bindPixels")
    public void bindPixels(@Valid @RequestBody BindPixelsRequest request) {
        service.bindPixels(request);
    }

    @Operation(summary = "获取证书")
    @PostMapping("/getCertificate")
    @SaCheckPermission("top-level-domain.get_certificate")
    public GetCertificateResp getCertificate(@Valid @RequestBody GetCertificateReq request) {
        return service.getCertificate(request);
    }

    @Operation(summary = "更新证书")
    @PostMapping("/updateCertificate")
    @SaCheckPermission("top-level-domain.update_certificate")
    public GetCertificateResp updateCertificate(@Valid @RequestBody UpdateCertificateReq request) {
        return service.updateCertificate(request);
    }

    @Operation(summary = "解析证书")
    @PostMapping("/parseCertificate")
    @SaCheckPermission("top-level-domain.parse_certificate")
    public SSLCertificateInfo parseCertificate(@Valid @RequestBody ParseCertificateReq request) {
        SSLCertificateInfo domainSslCertificateInfo = null;
        String[] certificates = request.getFullChain().split("-----END CERTIFICATE-----");
        // 遍历每个证书内容并解析
        for (String cert : certificates) {
            if (StrUtil.isBlank(cert.replaceAll("\n", "").trim())) {
                continue;
            }
            cert = cert + "-----END CERTIFICATE-----"; // 加上结束标签
            SSLCertificateInfo sslCertificateInfo = SslCertificateUtil.parseCertificate(cert);
            ClientResponseEnum.PARAMETER_ILLEGAL.notNull(sslCertificateInfo, "SSL公钥证书不正确");
            assert sslCertificateInfo != null;
            if (!StrUtil.isBlank(sslCertificateInfo.getCertExtentInfo())) {
                domainSslCertificateInfo = sslCertificateInfo;
            }
        }
        return domainSslCertificateInfo;
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return true;
    }
}
