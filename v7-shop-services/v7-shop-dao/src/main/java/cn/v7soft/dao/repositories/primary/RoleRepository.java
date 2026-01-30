package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleRepository extends BaseRepository<Role> {
    @Query("from Role where name=:name and (:id is null or id<>:id) and status='VALID'")
    Role findBySameName(@Param("name") String name, @Param("id") Long id);

    @Query("from Role where status='VALID' order by id desc")
    List<Role> findAllValidRole();

    // 查询 roleIds 中的有效角色，并按 id 降序排列
    @Query("from Role r where r.id in :roleIds and r.status = 'VALID' order by r.id desc")
    List<Role> listByRoleIds(@Param("roleIds") List<Long> roleIds);
}
