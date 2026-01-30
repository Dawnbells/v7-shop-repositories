package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreparatoryRequest {
    @Schema(title = "多媒体类型", example = "IMAGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private MediaType mediaType;

    @Schema(title = "文件夹CompatId", example = "1")
    private String folderCompatId;

    @NotBlank(message = "多媒体后缀不能为空")
    @Schema(title = "多媒体后缀", example = "jpeg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String suffix;

    @NotBlank(message = "相对路径不能为空")
    @Schema(title = "相对路径", example = "jpeg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String relativePath;

    @NotBlank(message = "文件名不能为空")
    @Schema(title = "文件名", example = "jpeg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileName;

    @PositiveOrZero
    @Schema(title = "文件大小", example = "2993202", requiredMode = Schema.RequiredMode.AUTO)
    private Long fileSize = 0L;
}
