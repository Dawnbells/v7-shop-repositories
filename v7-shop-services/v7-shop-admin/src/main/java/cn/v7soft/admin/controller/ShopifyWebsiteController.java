package cn.v7soft.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base62;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditShopifyWebsiteRequest;
import cn.v7soft.admin.controller.resp.ThirdPartyWebsiteResponse;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.utils.ShopifyHost;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Shopify 商城的新增/编辑。分页、删除、启停、订单同步复用 /third-party-website 下的接口
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

    @PostMapping("/doEdit")
    @Operation(summary = "新增或编辑Shopify商城")
    public ThirdPartyWebsiteResponse doEdit(@Valid @RequestBody EditShopifyWebsiteRequest request) {
        StpUtil.checkPermission(PERMISSION_PREFIX + (request.hasId() ? ".update" : ".create"));

        ThirdPartyWebsite dbEntity = null;
        if (request.hasId()) {
            dbEntity = service.getById(request.getIdLongValue());
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(dbEntity.getWebsiteType() == WebsiteTypeEnum.SHOPIFY,
                    "该商城不是Shopify类型");
        }
        Long currentId = dbEntity != null ? dbEntity.getId() : null;

        String handle = ShopifyHost.normalize(request.getHandle());
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(handle, "店铺的唯一标识不能为空");
        service.getByHandle(handle).ifPresent(existing ->
                ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(existing.getId().equals(currentId), "Handle已被占用: " + handle));

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
            website.setAuthStatus(ThirdPartyAuthStatusEnum.INIT);
            website.setLastSyncTime(LocalDateTime.now());
        }

        shopifyOrderSyncService.verifyAndUpdateAuthStatus(website);

        ThirdPartyWebsite saved = service.save(website.fillOwner());
        ThirdPartyWebsiteResponse response = ThirdPartyWebsiteResponse.convertEntity(saved);
        response.setId(String.valueOf(saved.getId()));
        response.setCompactId(Base62.encode(String.valueOf(saved.getId())));
        response.setStatus(saved.getStatus());
        return response;
    }

}
