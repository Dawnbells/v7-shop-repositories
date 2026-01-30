package cn.v7soft.dao.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum SystemUserType {
    /**
     * 管理员
     */
    ADMIN(0),
    /**
     * 公司管理员
     */
    COMPANY_ADMIN(10),
    /**
     * 深度部门访问，包含当前部门和所有下级子部门
     */
    DEEP_DEPARTMENT_MANAGER(100),
    /**
     * 当前部门访问
     */
    DEPARTMENT_MANAGER(101),
    /**
     * 部门树，所有父部门和当前部门以及所有下级子部门
     */
    DEPARTMENT_TREE(102),
    /**
     * 只能访问自己的
     */
    EMPLOYEE(200);
    private final int level;
    // 获取比指定 userType 等级低的所有类型
    public static List<SystemUserType> getEligibleTypes(SystemUserType newUserType) {
        return Arrays.stream(values())
                .filter(type -> type.getLevel() > newUserType.getLevel())
                .collect(Collectors.toList());
    }
}
