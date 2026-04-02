package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiTokenUsageRecordRepository extends BaseRepository<AiTokenUsageRecord> {

    Optional<AiTokenUsageRecord> findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(
            String contentHash, String targetLanguage);
}
