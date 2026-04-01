package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ImageTranslation;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageTranslationRepository extends BaseRepository<ImageTranslation> {

    Optional<ImageTranslation> findBySourceFileIdAndLanguageId(Long sourceFileId, Long languageId);
}
