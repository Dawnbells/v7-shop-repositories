package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditWebsiteProtocolRequest extends IdRequest {

    /**
     * 分组名
     */
    @NotBlank(message = "分组名称不能为空")
    @Schema(title = "分组名称", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /**
     * 分组排序
     */
    @Positive(message = "分组排序序号不能为空")
    @Schema(title = "分组排序序号", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private int sort;
    /**
     * 语言代码
     */
    @NotBlank(message = "语言ID不能为空")
    @Schema(title = "语言ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String languageId;
}
