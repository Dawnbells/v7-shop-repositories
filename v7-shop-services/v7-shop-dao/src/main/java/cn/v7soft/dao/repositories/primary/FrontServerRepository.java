
package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.FrontServer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FrontServerRepository extends BaseRepository<FrontServer> {

    /**
     * 根据服务器名称查找服务器
     *
     * @param name 服务器名称
     * @return 服务器列表
     */
    Optional<FrontServer> findByName(String name);

    /**
     * 查找当前有效域名解析数量大于指定值的服务器
     *
     * @param minActiveResolutionCount 最小有效解析数量
     * @return 服务器列表
     */
    @Query("SELECT fs FROM FrontServer fs WHERE fs.activeResolutionCount > :minActiveResolutionCount")
    List<FrontServer> findServersByActiveResolutionCount(@Param("minActiveResolutionCount") int minActiveResolutionCount);
}
