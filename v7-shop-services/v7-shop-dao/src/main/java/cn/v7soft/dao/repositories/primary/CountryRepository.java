package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Country;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface CountryRepository extends BaseRepository<Country> {
    @Query("from Country where name = :name and (:id is null or id <> :id) and status = 'VALID'")
    Country findByName(@Param("name") String name, @Param("id") Long id);

    @Query("from Country where code = :code")
    Optional<Country> getByCode(@Param("code") String upperCase);

    List<Country> findAllByCodeIn(Collection<String> codes);
}
