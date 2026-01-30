package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.core.controller.validator.annotation.ListPattern;
import cn.v7soft.dao.enums.ArticleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 用于编辑文章信息的请求类。
 */
@Getter
@Setter
public class EditArticleRequest extends IdRequest {
    @NotBlank(message = "文章名称不能为空")
    @Schema(title = "文章名称", example = "如何学习Java", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "文章标题不能为空")
    @Schema(title = "文章标题", example = "如何学习Java", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "文章内容不能为空")
    @Schema(title = "文章内容", example = "这是文章内容...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @NotBlank(message = "文章描述不能为空")
    @Schema(title = "文章描述", example = "这是文章描述...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @NotNull(message = "文章类型不能为空")
    @Schema(title = "文章类型", example = "BLOG", requiredMode = Schema.RequiredMode.REQUIRED)
    private ArticleType articleType;


    @Schema(title = "语言ID", example = "1")
    private String languageId;


}
