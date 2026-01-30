package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class QueryWebsiteProtocolRequest extends BasePageRequest {
    /**
     * 语言代码
     */
    @NotBlank(message = "语言ID")
    @Schema(title = "语言ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String languageId;
}
