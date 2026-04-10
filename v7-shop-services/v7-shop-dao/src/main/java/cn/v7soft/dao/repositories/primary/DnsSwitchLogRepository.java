package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.DnsSwitchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DnsSwitchLogRepository extends JpaRepository<DnsSwitchLog, Long> {
    List<DnsSwitchLog> findByAcknowledgedFalseOrderBySwitchedAtDesc();
}
