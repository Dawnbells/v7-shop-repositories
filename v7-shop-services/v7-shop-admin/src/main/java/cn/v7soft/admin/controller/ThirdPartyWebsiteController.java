package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.req.CountThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.EditThirdPartyWebsiteRequest;
import cn.v7soft.admin.controller.req.QueryThirdPartyWebsiteRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.resp.CountThirdPartyOrderResponse;
import cn.v7soft.admin.controller.resp.ThirdPartyWebsiteResponse;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Validated
@RestController
@RequestMapping("/third-party-website")
@Tag(name = "第三方网站管理")
public class ThirdPartyWebsiteController extends BaseDataRangeController<ThirdPartyWebsite, IThirdPartyWebsiteService, ThirdPartyWebsiteResponse, QueryThirdPartyWebsiteRequest, EditThirdPartyWebsiteRequest> {

    protected ThirdPartyWebsiteController(IThirdPartyWebsiteService service) {
        super(service);
    }

    @Override
    protected ThirdPartyWebsiteResponse convertEntity(ThirdPartyWebsite entity) {
        return ThirdPartyWebsiteResponse.convertEntity(entity);
    }

    @Override
    protected ThirdPartyWebsite convertRequest(@Nullable ThirdPartyWebsite dbEntity, EditThirdPartyWebsiteRequest request) {
        service.getByHandle(request.getHandle()).ifPresent(existing -> {
            boolean isDuplicate = dbEntity == null || !existing.getId().equals(dbEntity.getId());
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(!isDuplicate, "Handle已被占用: " + request.getHandle());
        });

        service.getByToken(request.getToken()).ifPresent(existing -> {
            boolean isDuplicate = dbEntity == null || !existing.getId().equals(dbEntity.getId());
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(!isDuplicate, "Token已被占用，请检查是否重复绑定");
        });

        ThirdPartyWebsite website = Optional.ofNullable(dbEntity).orElse(ThirdPartyWebsite.builder().build());
        website.setNickName(request.getNickName());
        website.setHandle(request.getHandle());
        website.setToken(request.getToken());
        website.setWebsiteType(request.getWebsiteType());

        if (dbEntity == null) {
            website.setAuthStatus(ThirdPartyAuthStatusEnum.INIT);
            website.setLastSyncTime(LocalDateTime.now());
        }

        service.verifyAndUpdateAuthStatus(website);
        return website;
    }

    @PostMapping("/count-orders")
    public CountThirdPartyOrderResponse countOrders(@Valid @RequestBody CountThirdPartyOrdersRequest request) {
        return service.countOrders(request);
    }

    @PostMapping("/submit-sync-orders")
    public Long submitSyncOrders(@Valid @RequestBody SyncThirdPartyOrdersRequest request) {
        return service.submitSyncOrders(request);
    }

    @Override
    protected String getPermissionPrefix() {
        return "third-party-website";
    }
}
