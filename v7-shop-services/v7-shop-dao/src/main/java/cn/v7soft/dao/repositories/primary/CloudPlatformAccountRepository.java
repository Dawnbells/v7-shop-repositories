package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CloudPlatformAccountRepository extends BaseRepository<CloudPlatformAccount> {
    /**
     * 根据名称和排除指定ID查询云平台账号
     */
    @Query("FROM CloudPlatformAccount WHERE name = :name AND (:id IS NULL OR id <> :id) AND status = 'VALID'")
    CloudPlatformAccount findBySameName(@Param("name") String name, @Param("id") Long id);
}
