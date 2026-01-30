package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.common.controller.resp.LanguageResponse;
import cn.v7soft.dao.entities.primary.Article;
import cn.v7soft.dao.enums.ArticleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 用于返回文章信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "文章信息响应")
public class ArticleResponse extends DataRangeResponse {

    @Schema(title = "文章名称", example = "如何学习Java")
    private String name;

    @Schema(title = "文章标题", example = "如何学习Java")
    private String title;

    @Schema(title = "文章内容", example = "这是文章内容...")
    private String content;

    @Schema(title = "文章类型", example = "BLOG")
    private ArticleType articleType;

    @Schema(title = "文章描述", example = "这是文章描述")
    private String description;

    @Schema(title = "语言信息")
    private LanguageResponse language;

    /**
     * 从 `Article` 实体转换为 `ArticleResponse` 的静态方法。
     */
    public static ArticleResponse convertEntity(Article article) {
        return filling(article, ArticleResponse.builder()
                .name(article.getName())
                .title(article.getTitle())
                .content(article.getContent())
                .description(article.getDescription())
                .articleType(article.getArticleType())
                .language(LanguageResponse.convertEntity(article.getLanguage()))
                .build());
    }
}
