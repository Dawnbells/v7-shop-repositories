package cn.v7soft.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base62;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditShopifyWebsiteRequest;
import cn.v7soft.admin.controller.req.ShopifyBindRequest;
import cn.v7soft.admin.controller.req.ShopifyInstallUrlRequest;
import cn.v7soft.admin.controller.resp.ShopifyInstallUrlResponse;
import cn.v7soft.admin.controller.resp.ThirdPartyWebsiteResponse;
import cn.v7soft.admin.service.IShopifyInstallService;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.utils.ShopifyHost;
import cn.v7soft.common.utils.DomainUtils;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Shopify 商城的新增/编辑、公开应用连接与绑定。分页、删除、启停、订单同步复用 /third-party-website 下的接口
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/third-party-website/shopify")
@Tag(name = "第三方网站管理")
public class ShopifyWebsiteController {
    private static final String PERMISSION_PREFIX = "third-party-website";

    private final IThirdPartyWebsiteService service;
    private final IShopifyOrderSyncService shopifyOrderSyncService;
    private final IShopifyInstallService installService;

    @PostMapping("/doEdit")
    @Operation(summary = "新增或编辑Shopify商城", description = "新增为凭证模式；公开应用记录只能修改店铺名称与币种模式")
    public ThirdPartyWebsiteResponse doEdit(@Valid @RequestBody EditShopifyWebsiteRequest request) {
        StpUtil.checkPermission(PERMISSION_PREFIX + (request.hasId() ? ".update" : ".create"));

        ThirdPartyWebsite dbEntity = null;
        if (request.hasId()) {
            dbEntity = service.getById(request.getIdLongValue());
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(dbEntity.getWebsiteType() == WebsiteTypeEnum.SHOPIFY,
                    "该商城不是Shopify类型");
            if (ShopifyAccessMode.isPublicApp(dbEntity.getAccessMode())) {
                return editPublicApp(dbEntity, request);
            }
        }
        Long currentId = dbEntity != null ? dbEntity.getId() : null;

        String handle = ShopifyHost.normalize(request.getHandle());
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(handle, "店铺的唯一标识不能为空");
        service.getByHandle(handle).ifPresent(existing ->
                ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(existing.getId().equals(currentId), "Handle已被占用: " + handle));

        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getClientId(), "Client ID不能为空");
        String clientSecret = StrUtil.isNotBlank(request.getClientSecret())
                ? request.getClientSecret().trim()
                : (dbEntity != null ? dbEntity.getClientSecret() : null);
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(clientSecret, "Client Secret不能为空");

        ThirdPartyWebsite website = dbEntity != null ? dbEntity : ThirdPartyWebsite.builder().build();
        website.setNickName(request.getNickName());
        website.setHandle(handle);
        website.setClientId(request.getClientId().trim());
        website.setClientSecret(clientSecret);
        website.setWebsiteType(WebsiteTypeEnum.SHOPIFY);
        website.setCurrencyMode(request.getCurrencyMode() != null ? request.getCurrencyMode() : CurrencyMode.SHOP_MONEY);

        if (dbEntity == null) {
            website.setAccessMode(ShopifyAccessMode.CREDENTIALS);
            website.setAuthStatus(ThirdPartyAuthStatusEnum.INIT);
            website.setLastSyncTime(LocalDateTime.now());
        }

        shopifyOrderSyncService.verifyAndUpdateAuthStatus(website);

        return toResponse(service.save(website.fillOwner()));
    }

    @PostMapping("/install-url")
    @Operation(summary = "生成Shopify公开应用授权地址", description = "带 id 表示重新连接已有的公开应用记录")
    public ShopifyInstallUrlResponse installUrl(@Valid @RequestBody ShopifyInstallUrlRequest request,
                                                HttpServletRequest httpRequest) {
        StpUtil.checkPermission(PERMISSION_PREFIX + (request.hasId() ? ".update" : ".create"));
        String returnOrigin = header(httpRequest, "Origin");
        if (returnOrigin == null) {
            returnOrigin = header(httpRequest, "Referer");
        }
        String topLevelDomain = DomainUtils.getOriginTopLevelDomain(httpRequest);
        String authorizeUrl = installService.createAdminAuthorizeUrl(
                request.hasId() ? request.getIdLongValue() : null,
                request.getNickName(), request.getHandle(), request.getCurrencyMode(),
                returnOrigin, topLevelDomain, TenantContext.getCurrentTenant(), SaSessionUtil.getLoginUser().getLongId());
        return new ShopifyInstallUrlResponse(authorizeUrl);
    }

    @PostMapping("/bind")
    @Operation(summary = "绑定从Shopify发起安装的店铺", description = "ticket 为落地页跳转带来的 bindTicket")
    public ThirdPartyWebsiteResponse bind(@Valid @RequestBody ShopifyBindRequest request) {
        StpUtil.checkPermission(PERMISSION_PREFIX + ".create");
        ThirdPartyWebsite saved = installService.bind(request.getTicket(), request.getNickName(), request.getCurrencyMode(),
                TenantContext.getCurrentTenant(), SaSessionUtil.getLoginUser().getLongId());
        return toResponse(saved);
    }

    /**
     * 公开应用记录：只更新店铺名称与币种模式，不重新验证凭证、不改 handle/clientId/secret
     */
    private ThirdPartyWebsiteResponse editPublicApp(ThirdPartyWebsite dbEntity, EditShopifyWebsiteRequest request) {
        dbEntity.setNickName(request.getNickName().trim());
        if (request.getCurrencyMode() != null) {
            dbEntity.setCurrencyMode(request.getCurrencyMode());
        }
        return toResponse(service.save(dbEntity));
    }

    private static ThirdPartyWebsiteResponse toResponse(ThirdPartyWebsite saved) {
        ThirdPartyWebsiteResponse response = ThirdPartyWebsiteResponse.convertEntity(saved);
        response.setId(String.valueOf(saved.getId()));
        response.setCompactId(Base62.encode(String.valueOf(saved.getId())));
        response.setStatus(saved.getStatus());
        return response;
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        // 隐私模式下浏览器可能发送字面量 "null"
        return StrUtil.isBlank(value) || "null".equalsIgnoreCase(value.trim()) ? null : value.trim();
    }
}
