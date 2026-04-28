package cn.v7soft.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditAiAccountRequest;
import cn.v7soft.admin.controller.req.QueryAiAccountRequest;
import cn.v7soft.admin.controller.resp.AiAccountResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.dao.entities.primary.AiAccount;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Validated
@RestController
@RequestMapping("/ai-account")
@Tag(name = "AI账号管理")
public class AiAccountController extends BaseDataRangeController<AiAccount, IAiAccountService, AiAccountResponse, QueryAiAccountRequest, EditAiAccountRequest> {

    protected AiAccountController(IAiAccountService service) {
        super(service);
    }

    @Override
    protected QueryPageRequest<AiAccount> convertQueryPageRequest(QueryAiAccountRequest request) {
        return super.convertQueryPageRequest(request)
                .addConstraint(StrUtil.isNotBlank(request.getName()), LikeAttribute.builder().name("name").value(request.getName()).build())
                .addConstraint(request.getProvider() != null, EqualsQueryAttribute.builder().name("provider").value(request.getProvider()).build())
                .addConstraint(request.getApiChannel() != null, EqualsQueryAttribute.builder().name("apiChannel").value(request.getApiChannel()).build())
                .addConstraint(request.getEnabled() != null, EqualsQueryAttribute.builder().name("enabled").value(request.getEnabled()).build());
    }

    @Override
    protected AiAccountResponse convertEntity(AiAccount entity) {
        return AiAccountResponse.convertEntity(entity);
    }

    @Override
    protected AiAccount convertRequest(@Nullable AiAccount dbEntity, EditAiAccountRequest request) {
        AiAccount entity = Optional.ofNullable(dbEntity).orElse(AiAccount.builder().build());
        BeanUtil.copyProperties(request, entity);
        entity.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return entity;
    }

    @Override
    protected String getPermissionPrefix() {
        return "ai-account";
    }
}
