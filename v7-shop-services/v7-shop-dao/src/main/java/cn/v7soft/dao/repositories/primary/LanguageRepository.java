package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Language;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LanguageRepository extends BaseRepository<Language> {
    @Query("from Language where name=:name and (:id is null or id<>:id) and status='VALID'")
    Language findBySameName(@Param("name") String name, @Param("id") Long id);

    @Query("from Language where code = :languageCode")
    Optional<Language> getByCode(@Param("languageCode") String languageCode);
}
