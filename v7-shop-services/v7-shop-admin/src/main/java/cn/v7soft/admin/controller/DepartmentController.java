package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.EditDepartmentRequest;
import cn.v7soft.admin.controller.req.QueryDepartmentRequest;
import cn.v7soft.admin.controller.req.TreeDepartmentRequest;
import cn.v7soft.admin.controller.resp.DepartmentResponse;
import cn.v7soft.admin.service.IDepartmentService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/department")
@Tag(name = "账户中心/部门管理")
@Validated
public class DepartmentController extends BaseDataRangeController<Department, IDepartmentService, DepartmentResponse, QueryDepartmentRequest, EditDepartmentRequest> {
    protected DepartmentController(IDepartmentService service) {
        super(service);
    }

    @PostMapping("/getTree")
    @Operation(summary = "分页查询")
    @SaCheckPermission("department.tree")
    public List<DepartmentResponse> treeAllValidDepartment(@Valid @RequestBody(required = false) TreeDepartmentRequest request) {
        StatusEnum statusEnum = request == null ? null : request.getStatus();
        List<Department> topDepartments = service.treeAllValidTopDepartments(statusEnum);
        List<DepartmentResponse> list = topDepartments.stream().map(department -> {
            DepartmentResponse departmentResponse = convertEntityCopyId(department);
            departmentResponse.setChildren(deepConvertChildren(department, statusEnum));
            departmentResponse.setParentId(department.getParent() == null ? null : department.getParent().getId());
            return departmentResponse;
        }).toList();
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        if (!loginUser.isAdmin()) {
            List<Long> visibleDepartmentIds = new ArrayList<>(loginUser.getAccessDepartmentIds());
            if (Boolean.TRUE.equals(loginUser.getIsCrossDepartment()) && loginUser.getManageDepartmentIds() != null) {
                visibleDepartmentIds.addAll(loginUser.getManageDepartmentIds());
            }
            list = list.stream()
                    .map(item -> filterDepartmentTree(item, visibleDepartmentIds))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        if (request != null && Boolean.TRUE.equals(request.getIsPrivateDomain())) {
            list = list.stream()
                    .map(DepartmentController::filterPrivateDomainTree)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return list;
    }

    @GetMapping("/info")
    @Operation(summary = "分页查询")
    @SaCheckPermission("department.info")
    public DepartmentResponse getDepartmentInfo() {
        return DepartmentResponse.convertEntity(service.getById(SaSessionUtil.getLoginUser().getDepartmentId()));
    }

    /**
     * 过滤 DepartmentResponse 树，仅保留在 accessDepartmentIds 中的部门。
     *
     * @param root                部门树的根节点
     * @param accessDepartmentIds 用户有权限访问的部门ID列表
     * @return 过滤后的部门树
     */
    public static DepartmentResponse filterDepartmentTree(DepartmentResponse root, List<Long> accessDepartmentIds) {
        if (root == null) {
            return null;
        }
        if (accessDepartmentIds.contains(Long.parseLong(root.getId()))) {
            // 包含了该部门
            return root;
        }
        if ((root.getChildren() == null || root.getChildren().isEmpty())) {
            // 叶子节点不在访问范围内，跳过该节点
            return null;
        }
        // 遍历子树
        List<DepartmentResponse> originalChildren = root.getChildren();
        List<DepartmentResponse> children = new ArrayList<>();
        for (DepartmentResponse child : originalChildren) {
            DepartmentResponse departmentResponse = filterDepartmentTree(child, accessDepartmentIds);
            if (departmentResponse != null) {
                children.add(departmentResponse);
            }
        }
        if (children.isEmpty()) {
            // 子树均不包含，跳过该节点
            return null;
        }
        // 重设子树
        root.setChildren(children);
        boolean allChildrenAccessible = children.size() == originalChildren.size()
                && children.stream().noneMatch(DepartmentResponse::isDisabled);
        root.setDisabled(!allChildrenAccessible);
        return root;
    }

    private List<DepartmentResponse> deepConvertChildren(Department department, StatusEnum status) {
        if (department.getChildren() == null || department.getChildren().isEmpty()) {
            return null;
        }
        return department.getChildren().stream()
                .filter(d -> status == null || d.getStatus() == status)
                .map(d -> {
                    DepartmentResponse departmentResponse = convertEntityCopyId(d);
                    departmentResponse.setChildren(deepConvertChildren(d, status));
                    departmentResponse.setParentId(d.getParent() != null ? d.getParent().getId() : null);
                    return departmentResponse;
                }).toList();
    }


    @SaCheckLogin
    @PostMapping("/switchPrivateDomain")
    @Operation(summary = "切换私域部门状态")
    public void switchPrivateDomain(@RequestBody @Valid IdRequest request) {
        Department department = service.getById(request.getIdLongValue());
        department.setIsPrivateDomain(!Boolean.TRUE.equals(department.getIsPrivateDomain()));
        service.save(department);
    }

    @Override
    protected DepartmentResponse convertEntity(Department department) {
        return DepartmentResponse.convertEntity(department);
    }

    @Override
    protected Department convertRequest(Department dbEntity, EditDepartmentRequest request) {
        Department department = Optional.ofNullable(dbEntity).orElse(Department.builder().build());
        BeanUtil.copyProperties(request, department);
        if (request.getParentId() != null && request.getParentId() > 0) {
            Department parent = service.getById(request.getParentId());
            department.setParent(parent);
        }
        return department;
    }


    @Override
    protected String getPermissionPrefix() {
        return "department";
    }

    private static DepartmentResponse filterPrivateDomainTree(DepartmentResponse node) {
        if (Boolean.TRUE.equals(node.getIsPrivateDomain())) {
            return node;
        }
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return null;
        }
        List<DepartmentResponse> filteredChildren = node.getChildren().stream()
                .map(DepartmentController::filterPrivateDomainTree)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (filteredChildren.isEmpty()) {
            return null;
        }
        node.setChildren(filteredChildren);
        node.setDisabled(true);
        return node;
    }
}
