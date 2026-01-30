package cn.v7soft.common.controller.req.attributes;

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
    private SystemUserDto owner;

    public SystemUserAccessDataRangeAttribute() {
        level = AccessDataRangeLevel.PERSON;
    }

    public SystemUserAccessDataRangeAttribute(AccessDataRangeLevel level) {
        this.level = level;
    }

    public SystemUserAccessDataRangeAttribute setOwner(SystemUser owner) {
        this.owner = SystemUserDto.convert(owner);
        return this;
    }

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (AccessDataRangeContext.isSilent()) {
            // 返回一个恒为 true 的谓词，不影响查询
            return criteriaBuilder.conjunction();
        }
        SystemUserDto user = owner == null? SaSessionUtil.getLoginUser(): owner;
        log.debug("loginUser: " + JSONUtil.toJsonStr(user) + ", level = " + level);
        if (user.getUserType() == SystemUserType.ADMIN || user.getUserType() == SystemUserType.COMPANY_ADMIN || level == AccessDataRangeLevel.COMPANY) {
            // 管理员不过滤
            return criteriaBuilder.conjunction();
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
