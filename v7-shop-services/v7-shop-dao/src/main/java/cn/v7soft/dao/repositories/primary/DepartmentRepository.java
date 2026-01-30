package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Department;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DepartmentRepository extends BaseRepository<Department> {
    @Query("from Department where parent is null and (:status is null or status=:status) order by sortOrder asc, id desc")
    List<Department> getAllTopDepartments(@Param("status") StatusEnum status);

    @Query("from Department where name=:name and (:id is null or id<>:id) and status='VALID'")
    Department findBySameName(String name, Long id);
}
