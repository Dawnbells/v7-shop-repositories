package cn.v7soft.dao.repositories.primary;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.TemporaryOrder;

public interface TemporaryOrderRepository extends BaseRepository<TemporaryOrder> {
    @Query("SELECT t FROM TemporaryOrder t WHERE t.reviewed = false ORDER BY t.orderTime ASC LIMIT 1")
    Optional<TemporaryOrder> getNextBotPendingOrder();

    @Query("SELECT t FROM TemporaryOrder t WHERE t.originOrderId = ?1")
    Optional<TemporaryOrder> findByOriginOrderId(String originOrderId);

    @Modifying
    @Query("UPDATE TemporaryOrder t SET t.reviewed = true, t.reviewTime = CURRENT_TIMESTAMP WHERE t.id = ?1")
    void markAsReviewed(Long id);
}
