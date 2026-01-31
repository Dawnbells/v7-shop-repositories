package cn.v7soft.dao.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 共享类型枚举
 */
@Getter
@AllArgsConstructor
public enum ShareType {
    PRIVATE("私有"),
    DEPARTMENT("部门共享"),
    COMPANY("公司共享");

    private final String name;
}
