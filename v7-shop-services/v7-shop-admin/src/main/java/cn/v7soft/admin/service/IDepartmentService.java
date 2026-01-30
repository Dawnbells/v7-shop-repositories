package cn.v7soft.admin.service;

import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Department;

import java.util.List;

public interface IDepartmentService extends IBaseDataRangeService<Department> {
    List<Department> treeAllValidTopDepartments(StatusEnum statusEnum);
}
