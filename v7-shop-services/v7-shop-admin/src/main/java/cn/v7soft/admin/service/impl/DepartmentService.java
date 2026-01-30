package cn.v7soft.admin.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.v7soft.admin.service.IDepartmentService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService extends BaseDataRangeService<Department, DepartmentRepository> implements IDepartmentService {

    public DepartmentService(DepartmentRepository departmentRepository) {
        super(departmentRepository);
    }

    @Override
    public List<Department> treeAllValidTopDepartments(StatusEnum statusEnum) {
        return repository.getAllTopDepartments(statusEnum);
    }

    @Override
    @Transactional
    public void deleteAll(List<Long> ids) {
        for (Long id: ids) {
            delete(id);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Department department = getById(id);
        department.setStatus(StatusEnum.DELETED);
        if(CollectionUtil.isNotEmpty(department.getChildren()) ) {
            deepChangedStatus(department);
        }
        save(department);
    }

    @Override
    @Transactional
    public void switchStatus(Long id, StatusEnum status) {
        super.switchStatus(id, status);
        Department department = getById(id);
        if (status != StatusEnum.VALID) {
            deepChangedStatus(department);
            save(department);
        }
        if (status == StatusEnum.VALID) {
            Department parent = department.getParent();
            while (parent != null) {
                parent.setStatus(status);
                save(parent);
                parent = parent.getParent();
            }
        }
    }

    @Override
    protected void checkKeyConstraint(Department data) {
        Department department = repository.findBySameName(data.getName(), data.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(department, "部门名不允许重复");
    }

    private void deepChangedStatus(Department department) {
        if (CollectionUtil.isNotEmpty(department.getChildren())) {
            for (Department d : department.getChildren()) {
                d.setStatus(department.getStatus());
                deepChangedStatus(d);
            }
        }
    }
}
