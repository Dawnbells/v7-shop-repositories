package cn.v7soft.admin.controller.req.attributes;

import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.tenant.AccessDataRangeContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Collections;
import java.util.List;

public class OrderAccessDataRangeAttribute implements QueryAttribute {
    private final AccessDataRangeLevel level;
    private final List<Long> specifiedDepartmentIds;
    private final boolean isExclude;
    private final SystemUserDto owner;
    private final ViewMode viewMode;

    public OrderAccessDataRangeAttribute(AccessDataRangeLevel level, List<Long> specifiedDepartmentIds,
                                         boolean isExclude, SystemUserDto owner, ViewMode viewMode) {
        this.level = level;
        this.specifiedDepartmentIds = specifiedDepartmentIds != null ? specifiedDepartmentIds : Collections.emptyList();
        this.isExclude = isExclude;
        this.owner = owner;
        this.viewMode = viewMode;
    }

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (AccessDataRangeContext.isSilent()) {
            return criteriaBuilder.conjunction();
        }
        SystemUserDto user = owner == null ? SaSessionUtil.getLoginUser() : owner;
        SystemUserType userType = user.getUserType();
        ViewMode vm = viewMode == null ? ViewMode.TEAM : viewMode;
        if (vm == ViewMode.PERSONAL) {
            userType = SystemUserType.EMPLOYEE;
        }

        if (userType == SystemUserType.ADMIN || userType == SystemUserType.COMPANY_ADMIN
                || level == AccessDataRangeLevel.COMPANY) {
            return criteriaBuilder.conjunction();
        }

        Join<T, Object> contextInfo = root.join("contextInfo", JoinType.LEFT);
        Join<T, Object> ownerJoin = root.join("owner", JoinType.LEFT);
        Join<Object, Object> ownerDepartment = ownerJoin.join("department", JoinType.LEFT);
        Expression<Long> departmentId = criteriaBuilder.<Long>coalesce()
                .value(contextInfo.get("departmentId"))
                .value(ownerDepartment.get("id"));

        if (level == AccessDataRangeLevel.SPECIFIED_DEPARTMENTS) {
            if (specifiedDepartmentIds.isEmpty()) {
                return isExclude ? criteriaBuilder.conjunction()
                        : personalPredicate(contextInfo, ownerJoin, criteriaBuilder, user);
            }
            CriteriaBuilder.In<Long> in = criteriaBuilder.in(departmentId);
            for (Long deptId : specifiedDepartmentIds) {
                in.value(deptId);
            }
            return isExclude ? criteriaBuilder.not(in) : in;
        }
        if (userType == SystemUserType.DEEP_DEPARTMENT_MANAGER || level == AccessDataRangeLevel.DEEP_DEPARTMENT) {
            CriteriaBuilder.In<Long> in = criteriaBuilder.in(departmentId);
            for (Long accessDepartmentId : user.getAccessDepartmentIds()) {
                in.value(accessDepartmentId);
            }
            return in;
        }
        if (userType == SystemUserType.DEPARTMENT_MANAGER || level == AccessDataRangeLevel.DEPARTMENT) {
            return criteriaBuilder.equal(departmentId, user.getDepartmentId());
        }
        if (level == AccessDataRangeLevel.DEPARTMENT_TREE) {
            CriteriaBuilder.In<Long> in = criteriaBuilder.in(departmentId);
            for (Long parentDepartmentId : user.getParentDepartmentIds()) {
                in.value(parentDepartmentId);
            }
            for (Long accessDepartmentId : user.getAccessDepartmentIds()) {
                in.value(accessDepartmentId);
            }
            return in;
        }
        return personalPredicate(contextInfo, ownerJoin, criteriaBuilder, user);
    }

    private Predicate personalPredicate(Join<?, Object> contextInfo, Join<?, Object> ownerJoin,
                                        CriteriaBuilder criteriaBuilder, SystemUserDto user) {
        return criteriaBuilder.or(
                criteriaBuilder.equal(contextInfo.get("salesUid"), user.getLongId()),
                criteriaBuilder.equal(ownerJoin.get("id"), user.getLongId())
        );
    }
}
