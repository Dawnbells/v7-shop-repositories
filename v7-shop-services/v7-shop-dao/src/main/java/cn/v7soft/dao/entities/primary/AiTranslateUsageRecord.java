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

/**
 * AI 翻译子任务计费记录。
 * <p>
 * 生命周期：
 * 1. loadTask 拆分子任务时创建，写入 frozenCredits（预估冻结积分）
 * 2. Provider 回调完成时，由 updateUsageRecord 写入实际 token 用量和 businessCredits
 * 3. Provider 回调失败时，由 accumulateUsageRecord 累加部分 token 消耗（跨重试累加）
 * 4. syncTaskStatus 检测全部子任务结束后，SUM(businessCredits) 结算
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_ai_translate_usage_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_atur2_task_subtask",
                        columnNames = {"task_id", "sub_task_id"})
        },
        indexes = {
                @Index(name = "idx_atur2_task_id", columnList = "task_id"),
                @Index(name = "idx_atur2_create_time", columnList = "create_time"),
                @Index(name = "idx_atur2_ai_account_create_time", columnList = "ai_account_id, create_time"),
        })
public class AiTranslateUsageRecord extends BaseDataRangeEntity {

    /** 关联的 AsyncTask ID */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 子任务标识，格式: taskId:TYPE:contentKey */
    @Column(name = "sub_task_id", nullable = false, length = 200)
    private String subTaskId;

    /** 执行该子任务的 AI 账号 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_account_id")
    private AiAccount aiAccount;

    /** 内容类型：TEXT / HTML / IMAGE */
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private TranslationContentType contentType;

    /** 内容哈希（TEXT/HTML 为 SHA256，IMAGE 为图片 ID） */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** 目标翻译语言名称 */
    @Column(name = "target_language", length = 50)
    private String targetLanguage;

    /** 是否命中翻译缓存（命中时不消耗实际 token） */
    @Builder.Default
    @Column(name = "cache_hit", nullable = false)
    private Boolean cacheHit = false;

    /** AI 模型标识 */
    @Column(name = "model", length = 50)
    private String model;

    /** 预估冻结积分（loadTask 时写入，用于任务级积分冻结） */
    @Builder.Default
    @Column(name = "frozen_credits")
    private Integer frozenCredits = 0;

    // --- 实际 token 用量（Provider 回调时写入/累加） ---

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

    // --- 业务 token（用于计费，按定价模型估算） ---

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

    /** API 侧实际费用（USD） */
    @Builder.Default
    @Column(name = "actual_cost", precision = 12, scale = 6)
    private BigDecimal actualCost = BigDecimal.ZERO;

    /** 按定价模型计算的业务费用（USD） */
    @Builder.Default
    @Column(name = "business_cost", precision = 12, scale = 6)
    private BigDecimal businessCost = BigDecimal.ZERO;

    /** 实际消耗积分 = CEIL(businessCost * 1000)，结算时 SUM 此字段 */
    @Builder.Default
    @Column(name = "business_credits")
    private Integer businessCredits = 0;

    /** 子任务执行耗时（毫秒） */
    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /** 图片翻译是否有图片输出 */
    @Column(name = "has_image_output")
    private Boolean hasImageOutput;

    /** 当前重试次数（跨重试累加 token 时更新） */
    @Builder.Default
    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    /** 是否已结算（settleTask 后标记为 true，防止重复结算） */
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
