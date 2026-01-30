package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditMultimediaFileRequest extends IdRequest {
    @NotBlank(message = "文件名不能为空")
    @Schema(title = "文件名", example = "example.jpg")
    private String name;

    @NotBlank(message = "后缀不能为空")
    @Schema(title = "后缀", example = "jpg")
    private String suffix;

    @Schema(title = "资源类型", example = "IMAGE")
    private String mediaType;
}
