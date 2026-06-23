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
     * 获取所有正在申请证书和排队申请证书的域名，不限公司和数据范围。
     * 按 id 升序（雪花ID≈创建顺序），使应用重启重排后队列恢复为符合直觉的先进先出。
     */
    @Query("FROM TopLevelDomain WHERE certificateRequestStatus='REQUESTING' OR certificateRequestStatus='QUEUE' ORDER BY id ASC")
    List<TopLevelDomain> findAllQueueOrRequesting();

    /**
     * 查询所有未删除的域名（跨租户），用于定时任务巡检。
     * 包含 VALID 和 INVALID 状态，走统一的巡检流程。
     * JOIN FETCH owner 避免 N+1 查询和潜在的 LazyInitializationException。
     */
    @Query("SELECT d FROM TopLevelDomain d LEFT JOIN FETCH d.owner WHERE d.status <> 'DELETED'")
    List<TopLevelDomain> findAllValidDomains();

    /**
     * 前端机 agent manifest 用：当前公司下所有「存在已绑定商城的活跃 WEBSITE 子域名」的一级域名。
     * 判定口径与旧 NginxConfigWriter 的写入条件对齐（绑定商城域名时才写 nginx 配置）。
     * 注意：依赖 Hibernate @TenantId 自动过滤当前公司，必须在已设置租户的请求上下文中调用。
     */
    @Query("SELECT DISTINCT d FROM TopLevelDomain d WHERE d.status <> 'DELETED' AND EXISTS (" +
            "SELECT s.id FROM SubDomain s WHERE s.parentDomain = d AND s.type = 'WEBSITE' " +
            "AND s.status <> 'DELETED' AND s.website IS NOT NULL)")
    List<TopLevelDomain> findAllAgentServableDomains();

    /**
     * 按域名名称查询未删除的一级域名（租户内）。前端机 agent 下载证书前用于校验域名归属。
     */
    @Query("FROM TopLevelDomain WHERE name = :name AND status <> 'DELETED'")
    List<TopLevelDomain> findValidByName(@Param("name") String name);
}
