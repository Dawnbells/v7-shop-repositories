package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditLanguageRequest extends IdRequest {
    @NotBlank(message = "语言名称不能为空")
    @Schema(title = "语言名称", example = "English", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "语言中文名称不能为空")
    @Schema(title = "语言中文名称", example = "英语", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cname;


    @NotBlank(message = "语言代码不能为空")
    @Schema(title = "语言代码", example = "en", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

}
