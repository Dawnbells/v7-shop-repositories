package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ProtectedDataAccessLog;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProtectedDataAccessLogRepository extends BaseRepository<ProtectedDataAccessLog> {

    /**
     * 删除早于指定时间的访问日志（保留期任务，调用方需在 silent 下执行以覆盖全租户）
     * @return 删除行数
     */
    @Modifying
    @Query("DELETE FROM ProtectedDataAccessLog l WHERE l.accessTime < :time")
    int deleteByAccessTimeBefore(@Param("time") LocalDateTime time);
}
