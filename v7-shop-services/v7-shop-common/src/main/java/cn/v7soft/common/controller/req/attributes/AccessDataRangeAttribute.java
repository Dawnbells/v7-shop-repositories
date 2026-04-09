package cn.v7soft.common.controller.req.attributes;

import cn.hutool.json.JSONUtil;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.tenant.AccessDataRangeContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
public class AccessDataRangeAttribute implements QueryAttribute {

    private final AccessDataRangeLevel level;
    private final List<Long> specifiedDepartmentIds;
    private SystemUserDto owner;
    private ViewMode viewMode;

    public AccessDataRangeAttribute() {
        level = AccessDataRangeLevel.PERSON;
        specifiedDepartmentIds = Collections.emptyList();
    }

    public AccessDataRangeAttribute(AccessDataRangeLevel level) {
        this.level = level;
        this.specifiedDepartmentIds = Collections.emptyList();
    }

    public AccessDataRangeAttribute(AccessDataRangeLevel level, List<Long> specifiedDepartmentIds) {
        this.level = level;
        this.specifiedDepartmentIds = specifiedDepartmentIds != null ? specifiedDepartmentIds : Collections.emptyList();
    }

    public AccessDataRangeAttribute setOwner(SystemUser owner) {
        this.owner = SystemUserDto.convert(owner);
        return this;
    }

    public AccessDataRangeAttribute setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode == null? ViewMode.TEAM: viewMode;
        return this;
    }

    public AccessDataRangeAttribute setOwner(SystemUserDto owner) {
        this.owner = owner;
        return this;
    }

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (AccessDataRangeContext.isSilent()) {
            // 返回一个恒为 true 的谓词，不影响查询
            return criteriaBuilder.conjunction();
        }
        SystemUserDto user = owner == null ? SaSessionUtil.getLoginUser() : owner;
        SystemUserType userType = user.getUserType();
        ViewMode vm = viewMode == null ? SaSessionUtil.getViewMode() : viewMode;
        if (vm == ViewMode.PERSONAL) {
            // 个人模式
            userType = SystemUserType.EMPLOYEE;
        }

        if (userType == SystemUserType.ADMIN || userType == SystemUserType.COMPANY_ADMIN || level == AccessDataRangeLevel.COMPANY) {
            // 管理员不过滤
            return criteriaBuilder.conjunction();
        }
        if (level == AccessDataRangeLevel.SPECIFIED_DEPARTMENTS) {
            if (specifiedDepartmentIds.isEmpty()) {
                return new AccessDataRangeAttribute().setOwner(user).setViewMode(vm)
                        .toPredicate(root, query, criteriaBuilder);
            }
            CriteriaBuilder.In<Object> in = criteriaBuilder.in(root.get("owner").get("department").get("id"));
            for (Long deptId : specifiedDepartmentIds) {
                in.value(deptId);
            }
            return in;
        }
        if (userType == SystemUserType.DEEP_DEPARTMENT_MANAGER || level == AccessDataRangeLevel.DEEP_DEPARTMENT) {
            // 深度部门 管理员-可以管理所有子部门
            CriteriaBuilder.In<Object> in = criteriaBuilder.in(root.get("owner").get("department").get("id"));
            for (Long accessDepartmentId : user.getAccessDepartmentIds()) {
                in.value(accessDepartmentId);
            }
            return in;
        }
        if (userType == SystemUserType.DEPARTMENT_MANAGER || level == AccessDataRangeLevel.DEPARTMENT) {
            // 只能管理自己所在的部门
            return criteriaBuilder.equal(root.get("owner").get("department").get("id"), user.getDepartmentId());
        }
        if (level == AccessDataRangeLevel.DEPARTMENT_TREE) {
            // 部门树，包含自己所在部门和所有子部门以及父级部门树
            CriteriaBuilder.In<Object> in = criteriaBuilder.in(root.get("owner").get("department").get("id"));
            for (Long parentDepartmentId : user.getParentDepartmentIds()) {
                in.value(parentDepartmentId);
            }
            for (Long accessDepartmentId : user.getAccessDepartmentIds()) {
                in.value(accessDepartmentId);
            }
            return in;
        }
        // 普通员工，仅查看自己
        return criteriaBuilder.equal(root.get("owner").get("id"), user.getId());
    }
}
