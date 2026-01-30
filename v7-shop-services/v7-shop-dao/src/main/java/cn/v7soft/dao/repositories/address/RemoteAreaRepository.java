package cn.v7soft.dao.repositories.address;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.address.RemoteArea;

public interface RemoteAreaRepository extends BaseRepository<RemoteArea> {

    @Query(value = "SELECT * FROM t_remote_area WHERE country_code = :countryCode AND postal_code = :postalCode LIMIT 1",
            nativeQuery = true)
    Optional<RemoteArea> isRemoteArea(@Param("countryCode") String countryCode,
                                      @Param("postalCode") String postalCode);

}
