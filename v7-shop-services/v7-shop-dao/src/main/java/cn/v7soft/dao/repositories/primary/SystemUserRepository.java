package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Role;
import cn.v7soft.dao.entities.primary.SystemUser;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface SystemUserRepository extends BaseRepository<SystemUser> {

    // 根据手机号查找用户和公司ID查找用户
    @Query("from SystemUser where telephone=:telephone and (userType='ADMIN' or companyId = :tenantId)")
    SystemUser findByTelephoneAndTenantId(@Param("telephone") String telephone, @Param("tenantId") Long tenantId);


    // 根据手机号查找用户
    SystemUser findByTelephone(String telephone);

    @EntityGraph(attributePaths = {"department"})
    @Query("from SystemUser u where u.telephone = :telephone and u.status <> 'DELETED'")
    List<SystemUser> findByTelephoneWithDepartment(@Param("telephone") String telephone, Pageable pageable);

    @EntityGraph(attributePaths = {"department"})
    @Query("from SystemUser u where u.name = :userName and u.status <> 'DELETED'")
    List<SystemUser> findByUserNameWithDepartment(@Param("userName") String userName, Pageable pageable);

    @Query("from SystemUser where telephone=:telephone and (:id is null or id<>:id) and status='VALID'")
    SystemUser findBySameUser(@Param("telephone") String telephone, @Param("id") Long id);

    // 查找分配了特定角色的所有用户
    @Query("SELECT su FROM SystemUser su JOIN su.roles r WHERE r = :role")
    List<SystemUser> findByRolesContaining(@Param("role") Role role);

    @Query(value = "SELECT * FROM t_system_users WHERE name=:userName and status <> 'DELETED' LIMIT 1 ", nativeQuery = true)
    Optional<SystemUser> findByUserName(@Param("userName") String userName);

    @Query(value = "SELECT * FROM t_system_users WHERE name=:userName and status = 'DELETED'  LIMIT 1 ", nativeQuery = true)
    Optional<SystemUser> findByDeletedUserNames(@Param("userName") String userName);

    @Modifying
    @Query("UPDATE SystemUser u SET u.frozenAiCredits = u.frozenAiCredits + :amount " +
           "WHERE u.id = :userId " +
           "AND u.monthlyAiCredits > 0 " +
           "AND (u.usedAiCredits + u.frozenAiCredits + :amount) <= u.monthlyAiCredits")
    int freezeCredits(@Param("userId") Long userId, @Param("amount") int amount);

    /**
     * 宽松冻结：只要 available > 0 即允许冻结（允许超额），用于子任务级积分冻结
     */
    @Modifying
    @Query("UPDATE SystemUser u SET u.frozenAiCredits = u.frozenAiCredits + :amount " +
           "WHERE u.id = :userId " +
           "AND u.monthlyAiCredits > 0 " +
           "AND (u.monthlyAiCredits - u.usedAiCredits - u.frozenAiCredits) > 0")
    void freezeCreditsIfPositive(@Param("userId") Long userId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE SystemUser u SET u.frozenAiCredits = u.frozenAiCredits - :freezeAmount, " +
           "u.usedAiCredits = u.usedAiCredits + :actualAmount " +
           "WHERE u.id = :userId")
    int settleCredits(@Param("userId") Long userId,
                      @Param("freezeAmount") int freezeAmount,
                      @Param("actualAmount") int actualAmount);

    @Modifying
    @Query("UPDATE SystemUser u SET u.frozenAiCredits = u.frozenAiCredits - :amount " +
           "WHERE u.id = :userId")
    int unfreezeCredits(@Param("userId") Long userId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE SystemUser u SET u.usedAiCredits = 0, u.frozenAiCredits = 0 " +
           "WHERE u.monthlyAiCredits > 0")
    int resetAllCredits();

    @Query("SELECT COALESCE(SUM(u.frozenAiCredits), 0) FROM SystemUser u WHERE u.companyId = :companyId")
    int sumFrozenCreditsByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT COALESCE(SUM(u.frozenAiCredits), 0) FROM SystemUser u WHERE u.id IN :userIds")
    int sumFrozenCreditsByUserIds(@Param("userIds") List<Long> userIds);

    @Query("SELECT u.id FROM SystemUser u WHERE u.department.id IN :departmentIds AND u.status = 'VALID'")
    List<Long> findUserIdsByDepartmentIds(@Param("departmentIds") List<Long> departmentIds);

    @Query("SELECT COALESCE(SUM(u.monthlyAiCredits), 0) FROM SystemUser u WHERE u.companyId = :companyId AND u.monthlyAiCredits > 0")
    int sumMonthlyAiCreditsByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT COALESCE(SUM(u.monthlyAiCredits), 0) FROM SystemUser u WHERE u.id IN :userIds AND u.monthlyAiCredits > 0")
    int sumMonthlyAiCreditsByUserIds(@Param("userIds") List<Long> userIds);
}
