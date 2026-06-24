package cn.v7soft.dao.repositories.primary;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Spu;

public interface SpuRepository extends BaseRepository<Spu> {

    @Query("from Spu where name=:name and (:id is null or id<>:id) and status='VALID'")
    Spu findBySameName(@Param("name") String name, @Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO t_website_spus (spu_id, website_id) VALUES (:spuId, :websiteId)", nativeQuery = true)
    void bindSpuToWebsite(@Param("spuId") Long spuId, @Param("websiteId") Long websiteId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM t_website_spus WHERE spu_id in(:spuIds) and website_id = :websiteId", nativeQuery = true)
    void unbindSpuToWebsite(@Param("spuIds") List<Long> spuIds, @Param("websiteId") Long websiteId);

    @Query("""
            SELECT MAX(s.code)
            FROM Spu s
            WHERE (
            (:departmentId IS NULL AND s.owner.department.id IS NULL)
            OR
            (:departmentId IS NOT NULL AND s.owner.department.id = :departmentId)
            )
            """)
    Integer getMaxSpuUserCode(@Param("departmentId") Long departmentId);

    @Query("select s from Spu s join s.websiteList w where s.id = :spuId and w.id = :websiteId")
    Optional<Spu> findByIdAndWebsiteId(@Param("spuId") Long spuId, @Param("websiteId") Long websiteId);

    @Modifying
    @Query("UPDATE Spu s SET s.updateTime = CURRENT_TIMESTAMP WHERE s.id = :id")
    void refreshUpdateTime(@Param("id") Long id);

    /**
     * 查询某员工名下全部 SPU 的 ID（@SQLRestriction 自动排除 DELETED，含 VALID/INVALID）。
     */
    @Query("select s.id from Spu s where s.owner.id = :ownerId")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 统计某员工名下 SPU 数量（自动排除 DELETED）。
     */
    long countByOwnerId(Long ownerId);

    /**
     * 判断目标员工名下是否已存在某来源 SPU 的有效副本（用于复制去重）。
     */
    boolean existsByOwnerIdAndSharedFromId(Long ownerId, Long sharedFromId);

}