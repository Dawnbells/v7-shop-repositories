package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditFolderRequest extends IdRequest {
    @NotBlank(message = "文件夹名称不能为空")
    @Schema(title = "文件夹名称", example = "Documents")
    private String name;

    @Schema(title = "是否敏感路径", example = "true")
    private Boolean isSensitive = Boolean.FALSE;
}
