package cn.v7soft.admin.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.EditArticleRequest;
import cn.v7soft.admin.controller.req.QueryArticleRequest;
import cn.v7soft.admin.controller.resp.ArticleResponse;
import cn.v7soft.admin.service.IArticleService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.dao.entities.primary.Article;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.enums.ArticleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/article")
@Tag(name = "文章管理")
public class ArticleController extends BaseDataRangeController<Article, IArticleService, ArticleResponse, QueryArticleRequest, EditArticleRequest> {

    protected ArticleController(IArticleService service) {
        super(service);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQueryProtocol")
    public List<ArticleResponse> remoteQueryProtocol(@RequestParam("query") String query, @RequestParam("languageId") String languageId) {
        QueryPageRequest<Article> request = this.convertQueryPageRequest(QueryArticleRequest.builder().pageNo(1).build());
        //noinspection DuplicatedCode
        request.addConstraint(StringUtils.hasText(query), LikeAttribute.builder().name("name").value(query.trim()).build())
                .addConstraint(StringUtils.hasText(query), LikeAttribute.builder().name("title").value(query.trim()).build())
                .add(EqualsQueryAttribute.builder().name("language.id").value(languageId).build())
                .add(EqualsQueryAttribute.builder().name("articleType").value(ArticleType.PROTOCOL.name()).build());
        return service.findPaginated(request)
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @Override
    protected ArticleResponse convertEntity(Article article) {
        return ArticleResponse.convertEntity(article);
    }

    @Override
    protected Article convertRequest(@Nullable Article dbEntity, EditArticleRequest request) {
        Article article = Optional.ofNullable(dbEntity).orElse(Article.builder().build());
        BeanUtil.copyProperties(request, article);
        article.setLanguage(Language.builder().id(Long.valueOf(request.getLanguageId())).build());
        return article;
    }

    @Override
    protected String getPermissionPrefix() {
        return "article";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        service.cleanupBeforeDelete(request);
        return true;
    }
}
