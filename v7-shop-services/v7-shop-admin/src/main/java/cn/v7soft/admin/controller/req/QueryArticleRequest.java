package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.ArticleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 用于查询文章信息的请求类，支持分页。
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QueryArticleRequest extends BasePageRequest {

    @Schema(title = "文章标题", example = "如何学习Java")
    private String title;

    @Schema(title = "文章类型", example = "PROTOCOL")
    private ArticleType articleType;
}
