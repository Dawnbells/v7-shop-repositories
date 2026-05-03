package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.TranslationContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAiTokenUsageRecordRequest extends BasePageRequest {

    @Schema(title = "任务ID", example = "1")
    private Long taskId;

    @Schema(title = "模型", example = "gemini-2.0-flash")
    private String model;

    @Schema(title = "内容类型", example = "TEXT")
    private TranslationContentType contentType;

    @Schema(title = "缓存命中")
    private Boolean cacheHit;
}
