package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageTranslationCacheRepository extends JpaRepository<ImageTranslationCache, Long> {

    Optional<ImageTranslationCache> findByImageHashAndLanguageId(String imageHash, Long languageId);
}
