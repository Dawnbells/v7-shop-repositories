package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.TextTranslationCache;
import cn.v7soft.dao.enums.TranslationContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TextTranslationCacheRepository extends JpaRepository<TextTranslationCache, Long> {

    Optional<TextTranslationCache> findByContentHashAndLanguageIdAndContentType(
            String contentHash, Long languageId, TranslationContentType contentType);
}
