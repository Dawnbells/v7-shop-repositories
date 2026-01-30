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
    @Query("FROM TopLevelDomain WHERE name = :name AND (:id IS NULL OR id <> :id) AND status = 'VALID'")
    TopLevelDomain findBySameName(@Param("name") String name, @Param("id") Long id);

    /**
     * 获取所有正在申请证书和排队申请证书的域名，不限公司和数据范围
     */
    @Query("FROM TopLevelDomain where certificateRequestStatus='REQUESTING' or certificateRequestStatus='QUEUE'")
    List<TopLevelDomain> findAllQueueOrRequesting();
}
