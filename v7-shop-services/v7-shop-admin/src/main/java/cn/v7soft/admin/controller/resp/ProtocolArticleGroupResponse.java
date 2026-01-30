package cn.v7soft.admin.controller.resp;

import java.util.ArrayList;
import java.util.List;

import cn.v7soft.common.controller.resp.LanguageResponse;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Article;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Setter
@Getter
public class ProtocolArticleGroupResponse extends IdResponse {
    private String name;
    private  int sort;
    private LanguageResponse language;
    private List<ArticleResponse> articleList;
    public static ProtocolArticleGroupResponse convertEntity(ProtocolArticleGroup group) {
        return ProtocolArticleGroupResponse.builder()
                .id(String.valueOf(group.getId()))
                .name(group.getName())
                .sort(group.getSort())
                .language(LanguageResponse.convertEntity(group.getLanguage()))
                .articleList(group.getArticleList().stream().map(ArticleResponse::convertEntity).toList())
                .build();
    }
}
