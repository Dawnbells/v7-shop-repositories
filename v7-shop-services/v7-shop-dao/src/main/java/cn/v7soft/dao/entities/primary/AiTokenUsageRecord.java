package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
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
                @UniqueConstraint(name = "uk_atur_task_hash_lang",
                        columnNames = {"task_id", "content_hash", "target_language"})
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

    @Column(name = "cache_hit", nullable = false)
    private Boolean cacheHit;

    @Column(name = "model", length = 50)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoke_mode", length = 20)
    private InvokeMode invokeMode;

    @Column(name = "actual_prompt_tokens")
    private Integer actualPromptTokens;

    @Column(name = "actual_completion_tokens")
    private Integer actualCompletionTokens;

    @Column(name = "actual_thinking_tokens")
    private Integer actualThinkingTokens;

    @Column(name = "actual_total_tokens")
    private Integer actualTotalTokens;

    @Column(name = "business_prompt_tokens")
    private Integer businessPromptTokens;

    @Column(name = "business_completion_tokens")
    private Integer businessCompletionTokens;

    @Column(name = "business_thinking_tokens")
    private Integer businessThinkingTokens;

    @Column(name = "business_total_tokens")
    private Integer businessTotalTokens;

    @Column(name = "actual_cost", precision = 12, scale = 6)
    private BigDecimal actualCost;

    @Column(name = "business_cost", precision = 12, scale = 6)
    private BigDecimal businessCost;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /**
     * 图片翻译结果是否有图片输出（用于区分费用计算方式：图片输出单价 vs 分辨率档位）
     */
    @Column(name = "has_image_output")
    private Boolean hasImageOutput;

    /**
     * = CEIL(businessCost * 1000)，整数 Credits，作为扣费依据
     */
    @Column(name = "business_credits")
    private Integer businessCredits;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "translated_text", columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "source_image_path")
    private String sourceImagePath;

    @Column(name = "translated_image_path")
    private String translatedImagePath;
}
