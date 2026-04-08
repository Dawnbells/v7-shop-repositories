package cn.v7soft.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.admin.controller.req.EditAiTokenUsageRecordRequest;
import cn.v7soft.admin.controller.req.QueryAiTokenUsageRecordRequest;
import cn.v7soft.admin.controller.resp.AiTokenUsageRecordResponse;
import cn.v7soft.admin.service.IAiTokenUsageRecordService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/aiTokenUsageRecord")
@Tag(name = "AI Token 使用记录")
public class AiTokenUsageRecordController extends BaseDataRangeController<AiTokenUsageRecord, IAiTokenUsageRecordService, AiTokenUsageRecordResponse, QueryAiTokenUsageRecordRequest, EditAiTokenUsageRecordRequest> {

    private final AsyncTaskRepository asyncTaskRepository;

    protected AiTokenUsageRecordController(IAiTokenUsageRecordService service, AsyncTaskRepository asyncTaskRepository) {
        super(service);
        this.asyncTaskRepository = asyncTaskRepository;
    }

    @Override
    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<AiTokenUsageRecordResponse> page(@Valid @RequestBody QueryAiTokenUsageRecordRequest request) {
        String permission = getPermissionPrefix() + ".page";
        StpUtil.checkPermission(permission);

        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        boolean isSuperAdmin = loginUser.getUserType() == SystemUserType.ADMIN;
        String imageBaseUrl = TenantContext.getCdnImageBaseUrl();

        Page<AiTokenUsageRecord> pageResult = service.findPaginated(convertQueryPageRequest(request));

        Set<Long> taskIds = pageResult.getContent().stream()
                .map(AiTokenUsageRecord::getTaskId)
                .collect(Collectors.toSet());
        Map<Long, String> taskNameMap = asyncTaskRepository.findAllById(taskIds).stream()
                .collect(Collectors.toMap(AsyncTask::getId, t -> t.getName() != null ? t.getName() : "", (a, b) -> a));

        Function<AiTokenUsageRecord, AiTokenUsageRecordResponse> converter = isSuperAdmin
                ? record -> AiTokenUsageRecordResponse.convertEntity(record, imageBaseUrl)
                : record -> AiTokenUsageRecordResponse.convertEntityLimited(record, imageBaseUrl);

        return pageResult.map(record -> {
            AiTokenUsageRecordResponse resp = converter.apply(record);
            resp.setTaskName(taskNameMap.getOrDefault(record.getTaskId(), ""));
            return resp;
        });
    }

    @Override
    protected QueryPageRequest<AiTokenUsageRecord> convertQueryPageRequest(QueryAiTokenUsageRecordRequest request) {
        if (cn.hutool.core.util.StrUtil.isBlank(request.getSortBy())) {
            request.setSortBy("taskId desc, id desc");
        }

        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        boolean isSuperAdmin = loginUser.getUserType() == SystemUserType.ADMIN;

        QueryPageRequest<AiTokenUsageRecord> pageRequest = super.convertQueryPageRequest(request);
        pageRequest.addConstraint(request.getTaskId() != null,
                EqualsQueryAttribute.builder().name("taskId").value(request.getTaskId()).build());
        pageRequest.addConstraint(cn.hutool.core.util.StrUtil.isNotBlank(request.getModel()),
                EqualsQueryAttribute.builder().name("model").value(request.getModel()).build());
        pageRequest.addConstraint(request.getInvokeMode() != null,
                EqualsQueryAttribute.builder().name("invokeMode").value(request.getInvokeMode()).build());
        pageRequest.addConstraint(request.getContentType() != null,
                EqualsQueryAttribute.builder().name("contentType").value(request.getContentType()).build());
        pageRequest.addConstraint(isSuperAdmin && request.getCacheHit() != null,
                EqualsQueryAttribute.builder().name("cacheHit").value(request.getCacheHit()).build());
        return pageRequest;
    }

    @Override
    protected AiTokenUsageRecordResponse convertEntity(AiTokenUsageRecord record) {
        return AiTokenUsageRecordResponse.convertEntity(record, TenantContext.getCdnImageBaseUrl());
    }

    @Override
    protected AiTokenUsageRecord convertRequest(@Nullable AiTokenUsageRecord dbEntity, EditAiTokenUsageRecordRequest request) {
        throw new UnsupportedOperationException("AiTokenUsageRecord 为只读记录，不支持编辑");
    }

    @Override
    protected String getPermissionPrefix() {
        return "aiTokenUsageRecord";
    }
}
