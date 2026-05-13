package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.TopLevelDomain;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopLevelDomainRepository extends BaseRepository<TopLevelDomain> {
    /**
     * 根据域名名称和排除指定ID查询一级域名
     */
    @Query("SELECT COUNT(d) FROM TopLevelDomain d WHERE d.name = :name AND (:id IS NULL OR d.id <> :id) AND d.status = 'VALID'")
    long countBySameName(@Param("name") String name, @Param("id") Long id);

    /**
     * 获取所有正在申请证书和排队申请证书的域名，不限公司和数据范围
     */
    @Query("FROM TopLevelDomain where certificateRequestStatus='REQUESTING' or certificateRequestStatus='QUEUE'")
    List<TopLevelDomain> findAllQueueOrRequesting();

    /**
     * 查询所有未删除的域名（跨租户），用于定时任务巡检。
     * 包含 VALID 和 INVALID 状态，走统一的巡检流程。
     * JOIN FETCH owner 避免 N+1 查询和潜在的 LazyInitializationException。
     */
    @Query("SELECT d FROM TopLevelDomain d LEFT JOIN FETCH d.owner WHERE d.status <> 'DELETED'")
    List<TopLevelDomain> findAllValidDomains();
}
