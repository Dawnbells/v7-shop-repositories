package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderStatisticsUserConfigRepository extends BaseRepository<OrderStatisticsUserConfig> {

    @Query("""
            FROM OrderStatisticsUserConfig c
            WHERE c.companyId = :companyId
              AND c.owner.id = :ownerId
              AND c.status <> 'DELETED'
            """)
    Optional<OrderStatisticsUserConfig> findByCompanyIdAndOwnerId(
            @Param("companyId") Long companyId,
            @Param("ownerId") Long ownerId
    );
}
