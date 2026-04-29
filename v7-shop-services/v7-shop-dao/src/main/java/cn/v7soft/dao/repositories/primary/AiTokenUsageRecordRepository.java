package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiTokenUsageRecordRepository extends BaseRepository<AiTokenUsageRecord> {

    @Query("SELECT COUNT(r) FROM AiTokenUsageRecord r WHERE r.taskId = :taskId")
    long countByTaskId(@Param("taskId") Long taskId);

    Optional<AiTokenUsageRecord> findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(
            String contentHash, String targetLanguage);

    @Query("SELECT COALESCE(SUM(r.businessCredits), 0) FROM AiTokenUsageRecord r WHERE r.taskId = :taskId")
    int sumBusinessCreditsByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT COUNT(r) > 0 FROM AiTokenUsageRecord r WHERE r.taskId = :taskId")
    boolean existsByTaskId(@Param("taskId") Long taskId);

    boolean existsByTaskIdAndContentHashAndTargetLanguage(Long taskId, String contentHash, String targetLanguage);

    @Query("SELECT COALESCE(SUM(r.businessPromptTokens), 0) FROM AiTokenUsageRecord r WHERE r.taskId = :taskId")
    int sumBusinessPromptTokensByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT COALESCE(SUM(r.businessCompletionTokens), 0) FROM AiTokenUsageRecord r WHERE r.taskId = :taskId")
    int sumBusinessCompletionTokensByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT COALESCE(SUM(r.businessThinkingTokens), 0) FROM AiTokenUsageRecord r WHERE r.taskId = :taskId")
    int sumBusinessThinkingTokensByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT COALESCE(SUM(r.businessCredits), 0) FROM AiTokenUsageRecord r WHERE r.createTime >= :start")
    int sumBusinessCreditsAfter(@Param("start") LocalDateTime start);

    @Query("SELECT COALESCE(SUM(r.businessCredits), 0) FROM AiTokenUsageRecord r WHERE r.createTime >= :start AND r.owner.id IN :ownerIds")
    int sumBusinessCreditsAfterByOwners(@Param("start") LocalDateTime start, @Param("ownerIds") List<Long> ownerIds);

    @Query("SELECT COUNT(r) FROM AiTokenUsageRecord r WHERE r.aiAccount.id = :aiAccountId AND r.cacheHit = false AND r.createTime >= :start")
    long countDailyCallsByAiAccount(@Param("aiAccountId") Long aiAccountId, @Param("start") LocalDateTime start);
}
