package cn.v7soft.admin.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditProtocolRequest;
import cn.v7soft.admin.controller.req.EditProtocolTranslationRequest;
import cn.v7soft.admin.controller.req.QueryEmployeeRequest;
import cn.v7soft.admin.controller.req.QueryProtocolRequest;
import cn.v7soft.admin.controller.resp.ProtocolResponse;
import cn.v7soft.admin.controller.resp.ProtocolSimpleResponse;
import cn.v7soft.admin.service.IProtocolService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.Protocol;
import cn.v7soft.dao.entities.primary.ProtocolTranslation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/protocol")
@Tag(name = "协议管理")
public class ProtocolController extends BaseDataRangeController<Protocol, IProtocolService, ProtocolResponse, QueryProtocolRequest, EditProtocolRequest> {

    protected ProtocolController(IProtocolService service) {
        super(service);
    }

    @Override
    protected QueryPageRequest<Protocol> convertQueryPageRequest(QueryProtocolRequest request) {
        return super.convertQueryPageRequest(request)
                .addConstraint(StrUtil.isNotBlank(request.getTitle()), LikeAttribute.builder().name("name").value(request.getTitle()).leftMatch(true).build());
    }

    @PostMapping("/editProtocolTranslation")
    @SaCheckPermission("protocol.editProtocolTranslation")
    @Operation(summary = "编辑协议")
    public void editProtocolTranslation(@Valid @RequestBody EditProtocolTranslationRequest request) {
        service.editProtocolTranslation(request);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    @SaCheckPermission("protocol.remoteQuery")
    public List<ProtocolSimpleResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<Protocol> request = QueryPageRequest.fromRequest(QueryEmployeeRequest.builder().pageNo(1).build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(LikeAttribute.builder().name("name").value("%" + query.trim() + "%").build())
                    .addConstraint(ConvertUtils.isLong(query), (protocol) -> EqualsQueryAttribute.builder().name("id").value(Long.valueOf(query.trim())).build())
                    .next();
        }
        return service.findPaginated(
                        request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build())
                ).stream()
                .map(ProtocolSimpleResponse::convertEntity)
                .collect(Collectors.toList());
    }

    @Override
    protected ProtocolResponse convertEntity(Protocol protocol) {
        return ProtocolResponse.convertEntity(protocol);
    }

    @Override
    protected Protocol convertRequest(@Nullable Protocol dbEntity, EditProtocolRequest request) {
        Protocol protocol = Optional.ofNullable(dbEntity).orElse(Protocol.builder().build());
        List<ProtocolTranslation> oldTranslations = protocol.getTranslations();
        List<ProtocolTranslation> newProtocolTranslations =
                request.getLanguageIds().stream().map(languageId -> {
                    // 看看老的 translation 里有没有
                    return oldTranslations.stream()
                            .filter(t -> t.getLanguage().getId().equals(languageId))
                            .findFirst()
                            .orElseGet(() -> {
                                // 没有就新建一个
                                ProtocolTranslation newTranslation = ProtocolTranslation.builder()
                                        .language(Language.builder().id(languageId).build())
                                        .articleGroupList(new ArrayList<>()) // 新建时，articleGroupList 为空
                                        .build();
                                newTranslation.setProtocol(protocol); // 设置双向关联
                                return newTranslation;
                            });

                }).toList();
        protocol.setName(request.getName());
        // 先清空原集合（Hibernate 会处理 orphanRemoval，删除数据库里的旧 translation）
        protocol.getTranslations().clear();
        // 再逐个添加新的 translation
        for (ProtocolTranslation newTranslation : newProtocolTranslations) {
            protocol.addTranslation(newTranslation); // 用实体里的 addTranslation 方法
        }
        if (request.getDefaultLanguageId() != null) {
            protocol.setDefaultLanguage(Language.builder().id(request.getDefaultLanguageId()).build());
        } else {
            protocol.setDefaultLanguage(null);
        }
        return protocol;
    }

    @Override
    protected String getPermissionPrefix() {
        return "protocol";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return false;
    }
}
