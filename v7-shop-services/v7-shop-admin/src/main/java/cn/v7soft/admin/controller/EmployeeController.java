package cn.v7soft.admin.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.DispatchDepartmentRequest;
import cn.v7soft.admin.controller.req.EditEmployeeRequest;
import cn.v7soft.admin.controller.req.GrantRoleRequest;
import cn.v7soft.admin.controller.req.QueryEmployeeRequest;
import cn.v7soft.admin.controller.req.SetAiCreditsRequest;
import cn.v7soft.admin.controller.resp.DepartmentResponse;
import cn.v7soft.admin.controller.resp.EmployeeResponse;
import cn.v7soft.admin.controller.resp.RoleResponse;
import cn.v7soft.admin.service.IEmployeeService;
import cn.v7soft.admin.service.ISpuService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.common.controller.req.attributes.SystemUserAccessDataRangeAttribute;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.InAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.NotQueryAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/employee")
@Tag(name = "账户中心/员工管理")
@Validated
public class EmployeeController extends BaseDataRangeController<SystemUser, IEmployeeService, EmployeeResponse, QueryEmployeeRequest, EditEmployeeRequest> {

    private final ISpuService spuService;

    protected EmployeeController(IEmployeeService service, ISpuService spuService) {
        super(service);
        this.spuService = spuService;
    }

    @Override
    protected QueryPageRequest<SystemUser> convertQueryPageRequest(QueryEmployeeRequest request) {
        return QueryPageRequest.<SystemUser>fromRequest(request).not("userType", SystemUserType.ADMIN)
                .or()
                .addConstraint(StrUtil.isNotBlank(request.getTitle()), LikeAttribute.builder().name("name").value(request.getTitle()).build())
                .addConstraint(ConvertUtils.isLong(request.getTitle()),(query)-> EqualsQueryAttribute.builder().name("telephone").value(ConvertUtils.parseLong(request.getTitle())).build())
                .next();
    }

    @PostMapping("/grantRole")
    @Operation(summary = "授予角色")
    @SaCheckPermission("employee.grant")
    public void doGrantRole(@Valid @RequestBody GrantRoleRequest request) {
        service.doGrantRole(request);
    }

    @PostMapping("/dispatchDepartment")
    @Operation(summary = "分发部门")
    @SaCheckPermission("employee.dispatch")
    public void dispatchDepartment(@Valid @RequestBody DispatchDepartmentRequest request) {
        service.dispatchDepartment(request);
    }

    @PostMapping("/setAiCredits")
    @Operation(summary = "设置员工AI额度")
    @SaCheckPermission("employee.edit")
    public void setAiCredits(@Valid @RequestBody SetAiCreditsRequest request) {
        SystemUser user = service.getById(request.getId());
        user.setMonthlyAiCredits(request.getMonthlyAiCredits());
        service.saveAndFlush(user);
    }

    @Override
    protected EmployeeResponse convertEntity(SystemUser systemUser) {
        EmployeeResponse.EmployeeResponseBuilder<?, ?> builder = EmployeeResponse.builder()
                .name(systemUser.getName())
                .gender(systemUser.getGender())
                .telephone(systemUser.getTelephone())
                .password(systemUser.getPlainPassword())
                .roles(
                        systemUser.getRoles().stream().filter(role -> role.getStatus() == StatusEnum.VALID)
                                .map(role -> RoleResponse.builder()
                                        .id(String.valueOf(role.getId()))
                                        .name(role.getName())
                                        .description(role.getDescription())
                                        .build()
                                ).collect(Collectors.toList()));
        builder.monthlyAiCredits(systemUser.getMonthlyAiCredits())
                .usedAiCredits(systemUser.getUsedAiCredits())
                .frozenAiCredits(systemUser.getFrozenAiCredits());
        Department department = systemUser.getDepartment();
        if (department != null) {
            builder.department(filling(department, DepartmentResponse.convertEntity(department)));
        }
        return builder.build();
    }

    @Override
    protected SystemUser convertRequest(SystemUser dbEntity, EditEmployeeRequest request) {
        SystemUserDto systemUserDto = SaSessionUtil.getLoginUser();
        SystemUserType userType = dbEntity == null ? SystemUserType.EMPLOYEE : dbEntity.getUserType();
        SystemUser user = Optional.ofNullable(dbEntity).orElse(SystemUser.builder().build());
        BeanUtil.copyProperties(request, user);
        user.setUserType(userType);
        user.setPlainPassword(request.getPassword());
        user.setPassword(BCrypt.hashpw(request.getPassword()));
        if (systemUserDto.getDepartmentId() != null && user.getDepartment() == null) {
            user.setDepartment(Department.builder().id(systemUserDto.getDepartmentId()).build());
        }
        return user;
    }

    @Override
    protected String getPermissionPrefix() {
        return "employee";
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    @SaCheckPermission("employee.remoteQuery")
    public List<EmployeeResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<SystemUser> request = QueryPageRequest.fromRequest(QueryEmployeeRequest.builder().pageNo(1).build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .addConstraint(ConvertUtils.isLong(query), systemUser -> EqualsQueryAttribute.builder().name("id").value(Long.valueOf(query.trim())).build())
                    .add(LikeAttribute.builder().name("name").value("%" + query.trim() + "%").build())
                    .add(LikeAttribute.builder().name("telephone").value("%" + query.trim() + "%").build())
                    .next();
        }
        request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        if (!loginUser.isAdmin()) {
            if (Boolean.TRUE.equals(loginUser.getIsCrossDepartment())) {
                List<Long> deptIds = loginUser.getManageDepartmentIds();
                if (deptIds != null && !deptIds.isEmpty()) {
                    request.add(InAttribute.<Long>builder().name("department.id").value(deptIds).build());
                }
            } else {
                request.or().add(new SystemUserAccessDataRangeAttribute()).next();
            }
        }
        return service.findOriginalPaginated(request).stream().map(this::convertEntityCopyId)
                .peek(item -> item.setTelephone(DesensitizedUtil.mobilePhone(item.getTelephone())))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("DuplicatedCode")
    @Operation(summary = "远程搜索SPU分享用户")
    @GetMapping("/remoteQuerySpuSharedUser")
    @SaCheckPermission("employee.remoteQuerySpuSharedUser")
    public List<EmployeeResponse> remoteQuerySpuSharedUser(@RequestParam("query") String query, @RequestParam String spuId) {
        ClientResponseEnum.PARAMETER_ILLEGAL.isLong(spuId, "参数错");
        Spu spu = spuService.findById(Long.valueOf(spuId)).orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("SPU不存在"));

        SystemUser spuOwner = spu.getOwner();
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();

        // 检查分享权限，管理员或者当前部门组长或者创建者本人才有权限分享
        boolean isAdmin = loginUser.isAdmin();
        boolean isDepartmentManager = loginUser.isDepartmentManager() && Objects.equals(spuOwner.getDepartment().getId(), loginUser.getDepartmentId());
        boolean isDeepDepartmentManager = loginUser.isDeepDepartmentManager() && loginUser.getAccessDepartmentIds().contains(spuOwner.getDepartment().getId());
        boolean isOwner = Objects.equals(loginUser.getLongId(), spuOwner.getId());
        ClientResponseEnum.NO_PERMISSION.assertTrue(isAdmin || isDepartmentManager || isDeepDepartmentManager || isOwner, "您无权限分享该SPU");

        QueryPageRequest<SystemUser> request = QueryPageRequest.fromRequest(QueryEmployeeRequest.builder().pageNo(1).pageSize(50).build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(LikeAttribute.builder().name("name").value("%" + query.trim() + "%").build())
                    .add(LikeAttribute.builder().name("telephone").value("%" + query.trim() + "%").build())
                    .next();
        }
        if (!isAdmin) {
            if (loginUser.getUserType() == SystemUserType.DEEP_DEPARTMENT_MANAGER) {
                List<Long> departments = new ArrayList<>();
                departments.add(loginUser.getDepartmentId());
                departments.addAll(loginUser.getAccessDepartmentIds());
                request.add(InAttribute.<Long>builder().name("department.id").value(departments).build());
            } else {
                request.add(EqualsQueryAttribute.builder().name("department.id").value(loginUser.getDepartmentId()).build());
            }
        }
        request.add(NotQueryAttribute.builder().name("userType").value(SystemUserType.ADMIN).build())
//                .add(NotQueryAttribute.builder().name("id").value(loginUser.getLongId()).build()) // 不限制自己，允许自己复制
                .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        return service.findOriginalPaginated(request).stream().map(this::convertEntityCopyId).collect(Collectors.toList());
    }
}
