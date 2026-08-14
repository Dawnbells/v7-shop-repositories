package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.TranslationContentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_ai_token_usage_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_atur_task_subtask",
                        columnNames = {"task_id", "sub_task_id"})
        },
        indexes = {
        @Index(name = "idx_atur_task_id", columnList = "task_id"),
        @Index(name = "idx_atur_content_hash", columnList = "content_hash"),
        @Index(name = "idx_atur_create_time", columnList = "create_time"),
        @Index(name = "idx_atur_ai_account_create_time", columnList = "ai_account_id, create_time"),
})
public class AiTokenUsageRecord extends BaseDataRangeEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "sub_task_id", length = 200)
    private String subTaskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_account_id")
    private AiAccount aiAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private TranslationContentType contentType;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "target_language", length = 50)
    private String targetLanguage;

    @Builder.Default
    @Column(name = "cache_hit", nullable = false)
    private Boolean cacheHit = false;

    /** 子任务被跳过翻译（如动图：webp 动图 / gif），不扣费、不冻结积分、不调用 Provider */
    @Builder.Default
    @Column(name = "skipped", nullable = false)
    private Boolean skipped = false;

    @Column(name = "model", length = 50)
    private String model;

    @Builder.Default
    @Column(name = "frozen_credits")
    private Integer frozenCredits = 0;

    @Builder.Default
    @Column(name = "actual_prompt_tokens")
    private Integer actualPromptTokens = 0;

    @Builder.Default
    @Column(name = "actual_completion_tokens")
    private Integer actualCompletionTokens = 0;

    @Builder.Default
    @Column(name = "actual_thinking_tokens")
    private Integer actualThinkingTokens = 0;

    @Builder.Default
    @Column(name = "actual_total_tokens")
    private Integer actualTotalTokens = 0;

    @Builder.Default
    @Column(name = "business_prompt_tokens")
    private Integer businessPromptTokens = 0;

    @Builder.Default
    @Column(name = "business_completion_tokens")
    private Integer businessCompletionTokens = 0;

    @Builder.Default
    @Column(name = "business_thinking_tokens")
    private Integer businessThinkingTokens = 0;

    @Builder.Default
    @Column(name = "business_total_tokens")
    private Integer businessTotalTokens = 0;

    @Builder.Default
    @Column(name = "actual_cost", precision = 12, scale = 6)
    private BigDecimal actualCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "business_cost", precision = 12, scale = 6)
    private BigDecimal businessCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "business_credits")
    private Integer businessCredits = 0;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "has_image_output")
    private Boolean hasImageOutput;
    @Column(name = "policy_fallback_reason", length = 160)
    private String policyFallbackReason;

    /**
     * 永久失败原因（errorCode: message，截断到 500 字）。
     * 有值代表这一行已经终态，用量列表不该再显示"翻译中..."。
     */
    @Column(name = "fail_reason", length = 500)
    private String failReason;


    @Builder.Default
    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    @Builder.Default
    @Column(name = "settled", nullable = false)
    private Boolean settled = false;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "translated_text", columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "source_image_path")
    private String sourceImagePath;

    @Column(name = "translated_image_path")
    private String translatedImagePath;
}
