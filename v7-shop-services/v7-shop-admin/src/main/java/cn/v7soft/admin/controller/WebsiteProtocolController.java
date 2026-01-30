package cn.v7soft.admin.controller;

import java.util.ArrayList;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.EditWebsiteProtocolRequest;
import cn.v7soft.admin.controller.req.QueryWebsiteProtocolRequest;
import cn.v7soft.admin.controller.resp.WebsiteProtocolResponse;
import cn.v7soft.admin.service.IWebsiteProtocolService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;
import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.dao.tenant.WebsiteContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/websiteProtocol")
@Tag(name = "文章协议分组管理")
public class WebsiteProtocolController extends BaseDataRangeController<ProtocolArticleGroup, IWebsiteProtocolService, WebsiteProtocolResponse, QueryWebsiteProtocolRequest, EditWebsiteProtocolRequest> {

    protected WebsiteProtocolController(IWebsiteProtocolService service) {
        super(service);
    }


    @Operation(summary = "绑定SPU到当前商城")
    @PostMapping("/bind-article/{protocolId}/{articleId}")
    public void bindArticleToProtocolGroup(@PathVariable Long protocolId, @PathVariable Long articleId) {
        service.bindArticleToProtocolGroup(protocolId, articleId);
    }

    @PostMapping("/unbind-article/{protocolId}/{articleId}")
    @Operation(summary = "根据ID删除")
    public void unbindArticleFromProtocolGroup(@PathVariable Long protocolId, @PathVariable Long articleId) {
        service.unbindArticleFromProtocolGroup(protocolId, articleId);
    }

    @Override
    protected QueryPageRequest<ProtocolArticleGroup> convertQueryPageRequest(QueryWebsiteProtocolRequest request) {
        if (!WebsiteContext.isWebsiteAdmin()) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("仅支持商城后台配置");
        }
        return super.convertQueryPageRequest(request)
                .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build())
                .add(EqualsQueryAttribute.builder()
                             .name("language.id")
                             .value(Long.valueOf(request.getLanguageId()))
                             .build())
                .add(EqualsQueryAttribute.builder()
                             .name("website.id")
                             .value(WebsiteContext.getCurrentWebsiteId())
                             .build());
    }

    @Override
    protected WebsiteProtocolResponse convertEntity(ProtocolArticleGroup protocolArticleGroup) {
        return WebsiteProtocolResponse.convertEntity(protocolArticleGroup);
    }

    @Override
    protected ProtocolArticleGroup convertRequest(@Nullable ProtocolArticleGroup dbEntity, EditWebsiteProtocolRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.isLong(request.getLanguageId());
        if (!WebsiteContext.isWebsiteAdmin()) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("仅支持商城后台配置");
        }
        ProtocolArticleGroup protocolArticleGroup = Optional.ofNullable(dbEntity).orElse(ProtocolArticleGroup.builder().build());
        BeanUtil.copyProperties(request, protocolArticleGroup);
        if (dbEntity != null) {
            protocolArticleGroup.setArticleList(new ArrayList<>());
        }
        protocolArticleGroup.setWebsite(Website.builder().id(WebsiteContext.getCurrentWebsiteId()).build());
        protocolArticleGroup.setLanguage(Language.builder().id(Long.valueOf(request.getLanguageId())).build());
        return protocolArticleGroup;
    }

    @Override
    protected String getPermissionPrefix() {
        return "websiteProtocol";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        service.deleteAllArticleUnderProtocolGroup(request);
        return true;
    }
}
