package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.TranslationCache;
import cn.v7soft.dao.enums.TranslationContentType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TranslationCacheRepository extends BaseRepository<TranslationCache> {

    Optional<TranslationCache> findByContentHashAndLanguageIdAndContentType(
            String contentHash, Long languageId, TranslationContentType contentType);
}
