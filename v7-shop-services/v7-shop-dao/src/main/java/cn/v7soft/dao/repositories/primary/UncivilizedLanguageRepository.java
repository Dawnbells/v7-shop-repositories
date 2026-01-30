package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.UncivilizedLanguage;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UncivilizedLanguageRepository extends BaseRepository<UncivilizedLanguage> {

    @Query("from UncivilizedLanguage where language.id = : languageId")
    List<UncivilizedLanguage> findAllByLanguage(@Param("languageId") long languageId);
}
