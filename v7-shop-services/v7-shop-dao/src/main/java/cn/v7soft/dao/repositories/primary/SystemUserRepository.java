package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Role;
import cn.v7soft.dao.entities.primary.SystemUser;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SystemUserRepository extends BaseRepository<SystemUser> {

    // 根据手机号查找用户和公司ID查找用户
    @Query("from SystemUser where telephone=:telephone and (userType='ADMIN' or companyId = :tenantId)")
    SystemUser findByTelephoneAndTenantId(@Param("telephone") String telephone, @Param("tenantId") Long tenantId);


    // 根据手机号查找用户
    SystemUser findByTelephone(String telephone);

    @Query("from SystemUser where telephone=:telephone and (:id is null or id<>:id) and status='VALID'")
    SystemUser findBySameUser(@Param("telephone") String telephone, @Param("id") Long id);

    // 查找分配了特定角色的所有用户
    @Query("SELECT su FROM SystemUser su JOIN su.roles r WHERE r = :role")
    List<SystemUser> findByRolesContaining(@Param("role") Role role);

    @Query(value = "SELECT * FROM t_system_users WHERE name=:userName and status <> 'DELETED' LIMIT 1 ", nativeQuery = true)
    Optional<SystemUser> findByUserName(@Param("userName") String userName);
}
