package cn.v7soft.common.controller.req.attributes;

import java.util.Collections;
import java.util.List;

import cn.hutool.json.JSONUtil;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.tenant.AccessDataRangeContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SystemUserAccessDataRangeAttribute implements QueryAttribute {

    private final AccessDataRangeLevel level;
    private final List<Long> specifiedDepartmentIds;
    private final boolean isExclude;
    private SystemUserDto owner;

    public SystemUserAccessDataRangeAttribute() {
        level = AccessDataRangeLevel.PERSON;
        specifiedDepartmentIds = Collections.emptyList();
        isExclude = false;
    }

    public SystemUserAccessDataRangeAttribute(AccessDataRangeLevel level) {
        this.level = level;
        this.specifiedDepartmentIds = Collections.emptyList();
        this.isExclude = false;
    }

    public SystemUserAccessDataRangeAttribute(AccessDataRangeLevel level, List<Long> specifiedDepartmentIds) {
        this.level = level;
        this.specifiedDepartmentIds = specifiedDepartmentIds != null ? specifiedDepartmentIds : Collections.emptyList();
        this.isExclude = false;
    }

    public SystemUserAccessDataRangeAttribute(AccessDataRangeLevel level, List<Long> specifiedDepartmentIds, boolean isExclude) {
        this.level = level;
        this.specifiedDepartmentIds = specifiedDepartmentIds != null ? specifiedDepartmentIds : Collections.emptyList();
        this.isExclude = isExclude;
    }

    public SystemUserAccessDataRangeAttribute setOwner(SystemUser owner) {
        this.owner = SystemUserDto.convert(owner);
        return this;
    }

    public SystemUserAccessDataRangeAttribute setOwner(SystemUserDto owner) {
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
        log.debug("loginUser: " + JSONUtil.toJsonStr(user) + ", level = " + level);
        if (user.getUserType() == SystemUserType.ADMIN || user.getUserType() == SystemUserType.COMPANY_ADMIN || level == AccessDataRangeLevel.COMPANY) {
            // 管理员不过滤
            return criteriaBuilder.conjunction();
        }
        if (level == AccessDataRangeLevel.SPECIFIED_DEPARTMENTS) {
            if (specifiedDepartmentIds.isEmpty()) {
                if (isExclude) {
                    return criteriaBuilder.conjunction();
                }
                return new SystemUserAccessDataRangeAttribute().setOwner(user)
                        .toPredicate(root, query, criteriaBuilder);
            }
            CriteriaBuilder.In<Object> in = criteriaBuilder.in(root.get("department").get("id"));
            for (Long deptId : specifiedDepartmentIds) {
                in.value(deptId);
            }
            if (isExclude) {
                return criteriaBuilder.not(in);
            }
            if (user.getUserType() == SystemUserType.DEPARTMENT_MANAGER) {
                in.value(user.getDepartmentId());
            }
            return in;
        }
        if (user.getUserType() == SystemUserType.DEEP_DEPARTMENT_MANAGER || level == AccessDataRangeLevel.DEEP_DEPARTMENT) {
            // 深度部门 管理员-可以管理所有子部门
            CriteriaBuilder.In<Object> in = criteriaBuilder.in(root.get("department").get("id"));
            for (Long accessDepartmentId : user.getAccessDepartmentIds()) {
                in.value(accessDepartmentId);
            }
            return in;
        }
        if (user.getUserType() == SystemUserType.DEPARTMENT_MANAGER || level == AccessDataRangeLevel.DEPARTMENT) {
            // 只能管理自己所在的部门
            return criteriaBuilder.equal(root.get("department").get("id"), user.getDepartmentId());
        }
        // 普通员工，进查看自己
        return criteriaBuilder.equal(root.get("id"), user.getId());
    }
}
