package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@SuperBuilder
@Schema(description = "AI Token 使用记录响应")
public class AiTokenUsageRecordResponse extends DataRangeResponse {

    @Schema(title = "任务ID")
    private Long taskId;

    @Schema(title = "任务名称")
    private String taskName;

    @Schema(title = "内容类型")
    private TranslationContentType contentType;

    @Schema(title = "目标语言")
    private String targetLanguage;

    @Schema(title = "模型")
    private String model;

    @Schema(title = "调用模式")
    private InvokeMode invokeMode;

    @Schema(title = "业务 Prompt Tokens")
    private Integer businessPromptTokens;

    @Schema(title = "业务 Completion Tokens")
    private Integer businessCompletionTokens;

    @Schema(title = "业务 Thinking Tokens")
    private Integer businessThinkingTokens;

    @Schema(title = "业务总 Tokens")
    private Integer businessTotalTokens;

    @Schema(title = "业务积分")
    private Integer businessCredits;

    @Schema(title = "耗时(ms)")
    private Long elapsedMs;

    @Schema(title = "是否有图片输出")
    private Boolean hasImageOutput;

    @Schema(title = "原文")
    private String sourceText;

    @Schema(title = "译文")
    private String translatedText;

    @Schema(title = "原图URL")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sourceImageUrl;

    @Schema(title = "译图URL")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String translatedImageUrl;

    // ---- ADMIN 专属字段，非 ADMIN 时为 null，序列化时跳过 ----

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "缓存命中（仅ADMIN）")
    private Boolean cacheHit;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "实际 Prompt Tokens（仅ADMIN）")
    private Integer actualPromptTokens;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "实际 Completion Tokens（仅ADMIN）")
    private Integer actualCompletionTokens;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "实际 Thinking Tokens（仅ADMIN）")
    private Integer actualThinkingTokens;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "实际总 Tokens（仅ADMIN）")
    private Integer actualTotalTokens;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "实际成本（仅ADMIN）")
    private BigDecimal actualCost;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "业务成本（仅ADMIN）")
    private BigDecimal businessCost;

    public static AiTokenUsageRecordResponse convertEntity(AiTokenUsageRecord record, String imageBaseUrl) {
        return filling(record, AiTokenUsageRecordResponse.builder()
                .taskId(record.getTaskId())
                .contentType(record.getContentType())
                .targetLanguage(record.getTargetLanguage())
                .model(record.getModel())
                .invokeMode(record.getInvokeMode())
                .businessPromptTokens(record.getBusinessPromptTokens())
                .businessCompletionTokens(record.getBusinessCompletionTokens())
                .businessThinkingTokens(record.getBusinessThinkingTokens())
                .businessTotalTokens(record.getBusinessTotalTokens())
                .businessCost(record.getBusinessCost())
                .businessCredits(record.getBusinessCredits())
                .elapsedMs(record.getElapsedMs())
                .hasImageOutput(record.getHasImageOutput())
                .sourceText(record.getSourceText())
                .translatedText(record.getTranslatedText())
                .sourceImageUrl(buildImageUrl(imageBaseUrl, record.getSourceImagePath()))
                .translatedImageUrl(buildImageUrl(imageBaseUrl, record.getTranslatedImagePath()))
                .cacheHit(record.getCacheHit())
                .actualPromptTokens(record.getActualPromptTokens())
                .actualCompletionTokens(record.getActualCompletionTokens())
                .actualThinkingTokens(record.getActualThinkingTokens())
                .actualTotalTokens(record.getActualTotalTokens())
                .actualCost(record.getActualCost())
                .build());
    }

    public static AiTokenUsageRecordResponse convertEntityLimited(AiTokenUsageRecord record, String imageBaseUrl) {
        return filling(record, AiTokenUsageRecordResponse.builder()
                .taskId(record.getTaskId())
                .contentType(record.getContentType())
                .targetLanguage(record.getTargetLanguage())
                .model(record.getModel())
                .invokeMode(record.getInvokeMode())
                .businessPromptTokens(record.getBusinessPromptTokens())
                .businessCompletionTokens(record.getBusinessCompletionTokens())
                .businessThinkingTokens(record.getBusinessThinkingTokens())
                .businessTotalTokens(record.getBusinessTotalTokens())
                .businessCredits(record.getBusinessCredits())
                .elapsedMs(record.getElapsedMs())
                .hasImageOutput(record.getHasImageOutput())
                .sourceText(record.getSourceText())
                .translatedText(record.getTranslatedText())
                .sourceImageUrl(buildImageUrl(imageBaseUrl, record.getSourceImagePath()))
                .translatedImageUrl(buildImageUrl(imageBaseUrl, record.getTranslatedImagePath()))
                .build());
    }

    private static String buildImageUrl(String imageBaseUrl, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        if (imageBaseUrl == null || imageBaseUrl.isBlank()) return relativePath;
        String base = imageBaseUrl.endsWith("/") ? imageBaseUrl : imageBaseUrl + "/";
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return base + path;
    }
}
