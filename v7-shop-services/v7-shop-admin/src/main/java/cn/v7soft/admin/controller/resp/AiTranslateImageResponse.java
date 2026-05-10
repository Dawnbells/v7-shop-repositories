package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(description = "AI翻译图片响应")
public class AiTranslateImageResponse {

    @Schema(title = "文件ID")
    private Long id;

    @Schema(title = "文件名")
    private String name;

    @Schema(title = "后缀")
    private String suffix;

    @Schema(title = "媒体类型")
    private String mediaType;

    @Schema(title = "相对路径")
    private String relativePath;

    @Schema(title = "绝对路径（CDN地址）")
    private String absolutionPath;
}
