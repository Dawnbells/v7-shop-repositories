package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.EditRoleRequest;
import cn.v7soft.admin.controller.req.GrantRoutersRequest;
import cn.v7soft.admin.controller.req.QueryRoleRequest;
import cn.v7soft.admin.controller.resp.RoleResponse;
import cn.v7soft.admin.service.IEmployeeService;
import cn.v7soft.admin.service.IRoleService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.dao.entities.primary.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/role")
@Tag(name = "账户中心/角色管理")
@Validated
public class RoleController extends BaseDataRangeController<Role, IRoleService, RoleResponse, QueryRoleRequest, EditRoleRequest> {
    private final IEmployeeService employeeService;
    protected RoleController(IRoleService service, IEmployeeService employeeService) {
        super(service);
        this.employeeService = employeeService;
    }

    @PostMapping("/getAll")
    @SaCheckPermission("role.getAll")
    @Operation(summary = "获取所有有效角色列表")
    public List<RoleResponse> getAllValidRole() {
        return service.getAllValid().stream().map(this::convertEntityCopyId).collect(Collectors.toList());
    }

    @PostMapping("/grantRouters")
    @SaCheckPermission("role.grant")
    @Operation(summary = "分配路由")
    public void grantRouters(@Valid @RequestBody GrantRoutersRequest request) {
        service.grantRouters(request);
    }

    @Override
    protected RoleResponse convertEntity(Role role) {
        return RoleResponse.builder()
                .name(role.getName())
                .description(role.getDescription())
                .isAuditOrders(role.getIsAuditOrders())
                .systemUserType(role.getUserType())
                .systemRouterIds(role.getSystemRouterList().stream().map(BaseEntity::getId).collect(Collectors.toList()))
                .build();
    }

    @Override
    protected Role convertRequest(Role dbEntity, EditRoleRequest request) {
        Role role = Optional.ofNullable(dbEntity).orElse(Role.builder().build());
        BeanUtil.copyProperties(request, role);
        role.setUserType(request.getSystemUserType());
        role.setIsAuditOrders(request.getIsAuditOrders());
        return role;
    }

    @Override
    protected Role doEditOperate(EditRoleRequest request) {
        Role role = super.doEditOperate(request);
        employeeService.changeUserTypeWithRole(role);
        return role;
    }

    @Override
    protected String getPermissionPrefix() {
        return "role";
    }
}
