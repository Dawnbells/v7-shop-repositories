package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderSearchPresetRepository extends BaseRepository<OrderSearchPreset> {

    @Query("""
            FROM OrderSearchPreset p
            WHERE p.owner.id = :ownerId
              AND p.pageType = :pageType
              AND p.name = :name
              AND p.status = 'VALID'
            """)
    Optional<OrderSearchPreset> findValidByOwnerAndPageTypeAndName(
            @Param("ownerId") Long ownerId,
            @Param("pageType") OrderSearchPresetPageType pageType,
            @Param("name") String name
    );

    @Query("""
            FROM OrderSearchPreset p
            WHERE p.owner.id = :ownerId
              AND p.pageType = :pageType
              AND p.status = 'VALID'
            ORDER BY
              CASE WHEN p.lastUsedTime IS NULL THEN 1 ELSE 0 END ASC,
              p.lastUsedTime DESC,
              p.createTime DESC
            """)
    List<OrderSearchPreset> findValidByOwnerAndPageTypeOrderByUsage(
            @Param("ownerId") Long ownerId,
            @Param("pageType") OrderSearchPresetPageType pageType
    );

    @Query("""
            FROM OrderSearchPreset p
            WHERE p.id = :id
              AND p.owner.id = :ownerId
              AND p.status = 'VALID'
            """)
    Optional<OrderSearchPreset> findValidByIdAndOwnerId(
            @Param("id") Long id,
            @Param("ownerId") Long ownerId
    );
}
