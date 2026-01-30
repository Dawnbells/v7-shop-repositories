package cn.v7soft.dao.repositories.primary;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.TemporaryOrder;

public interface TemporaryOrderRepository extends BaseRepository<TemporaryOrder> {
    @Query("SELECT to FROM TemporaryOrder to WHERE to.id > COALESCE((SELECT MAX(o.id) FROM Order o), 0) ORDER BY to.id ASC LIMIT 1")
    Optional<TemporaryOrder> getNextBotPendingOrder();

    @Query("SELECT to FROM TemporaryOrder to WHERE to.originOrderId = ?1")
    Optional<TemporaryOrder> findByOriginOrderId(String originOrderId);

}
