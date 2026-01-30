package cn.v7soft.admin.controller.resp;

import java.util.List;

import cn.v7soft.common.controller.resp.LanguageResponse;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;
import cn.v7soft.dao.entities.primary.ProtocolTranslation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Setter
@Getter
public class ProtocolTranslationResponse extends IdResponse {

    private LanguageResponse language;

    private List<ProtocolArticleGroupResponse> articleGroupList;

    public static ProtocolTranslationResponse convertEntity(ProtocolTranslation translation) {
        return ProtocolTranslationResponse.builder()
                .id(String.valueOf(translation.getId()))
                .language(LanguageResponse.builder()
                                          .id(String.valueOf(translation.getLanguage().getId()))
                                          .name(translation.getLanguage().getName())
                                          .code(translation.getLanguage().getCode())
                                          .build())
                .articleGroupList(translation.getArticleGroupList().stream()
                        .map(ProtocolArticleGroupResponse::convertEntity)
                        .toList())
                .build();
    }
}
