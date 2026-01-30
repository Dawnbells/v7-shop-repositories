package cn.v7soft.admin.controller.resp;

import java.util.List;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class WebsiteProtocolResponse extends IdResponse {

    /**
     * 分组名
     */
    private String name;

    /**
     * 分组排序
     */
    private int sort;

    /**
     * 底下的文章列表
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "protocolArticleGroup")
    private List<ArticleResponse> articleList;

    public static WebsiteProtocolResponse convertEntity(ProtocolArticleGroup protocolArticleGroup) {
        return WebsiteProtocolResponse.builder()
                .id(String.valueOf(protocolArticleGroup.getId()))
                .name(protocolArticleGroup.getName())
                .sort(protocolArticleGroup.getSort())
                .articleList(protocolArticleGroup.getArticleList().stream().map(ArticleResponse::convertEntity).toList())
                .build();
    }
}
