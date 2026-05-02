package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.AiTranslateUsageRecord;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiTranslateUsageRecordRepository extends BaseRepository<AiTranslateUsageRecord> {

    List<AiTranslateUsageRecord> findByTaskId(Long taskId);

    Optional<AiTranslateUsageRecord> findByTaskIdAndSubTaskId(Long taskId, String subTaskId);

    @Query("SELECT COALESCE(SUM(r.businessCredits), 0) FROM AiTranslateUsageRecord r " +
           "WHERE r.taskId = :taskId AND r.settled = false")
    int sumBusinessCreditsByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT COALESCE(SUM(r.frozenCredits), 0) FROM AiTranslateUsageRecord r " +
           "WHERE r.taskId = :taskId AND r.settled = false")
    int sumFrozenCreditsByTaskId(@Param("taskId") Long taskId);

    @Modifying
    @Query("UPDATE AiTranslateUsageRecord r SET r.settled = true WHERE r.taskId = :taskId")
    int markSettledByTaskId(@Param("taskId") Long taskId);

    Optional<AiTranslateUsageRecord> findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(
            String contentHash, String targetLanguage);
}
