package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.MerchandiseGroup;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface MerchandiseGroupRepository extends BaseRepository<MerchandiseGroup> {

    @Query(value = "SELECT * FROM t_merchandise_group as mg WHERE JSON_CONTAINS(mg.merchandises, JSON_QUOTE(:value), '$') = 1", nativeQuery = true)
    Optional<MerchandiseGroup> findByMerchandiseContains(@Param("value") String value);

}
