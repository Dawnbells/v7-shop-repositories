package cn.v7soft.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.service.ICloudPlatformAccountService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.common.controller.req.EditCloudPlatformAccountRequest;
import cn.v7soft.common.controller.req.QueryCloudPlatformAccountRequest;
import cn.v7soft.common.controller.resp.CloudPlatformAccountResponse;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/cloud-platform-account")
@Tag(name = "管理系统-云平台账户管理")
public class CloudPlatformAccountController extends BaseDataRangeController<CloudPlatformAccount, ICloudPlatformAccountService, CloudPlatformAccountResponse, QueryCloudPlatformAccountRequest, EditCloudPlatformAccountRequest> {
    protected CloudPlatformAccountController(ICloudPlatformAccountService service) {
        super(service);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<CloudPlatformAccountResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<CloudPlatformAccount> request = QueryPageRequest.fromRequest(QueryCloudPlatformAccountRequest.builder().pageNo(1).build());
        //noinspection DuplicatedCode
        if (StringUtils.hasText(query)) {
            request.add(
                    LikeAttribute.builder()
                            .name("name")
                            .value("%" + query.trim() + "%")
                            .build()
            );
        }
        return service.findPaginated(request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build()))
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @Override
    protected CloudPlatformAccountResponse convertEntity(CloudPlatformAccount entity) {
        return CloudPlatformAccountResponse.convertEntity(entity);
    }

    @Override
    protected CloudPlatformAccount convertRequest(@Nullable CloudPlatformAccount dbEntity, EditCloudPlatformAccountRequest request) {
        CloudPlatformAccount entity = Optional.ofNullable(dbEntity).orElse(CloudPlatformAccount.builder().build());
        BeanUtil.copyProperties(request, entity);
        return entity;
    }

    @Override
    protected String getPermissionPrefix() {
        return "cloud-platform-account";
    }
}
