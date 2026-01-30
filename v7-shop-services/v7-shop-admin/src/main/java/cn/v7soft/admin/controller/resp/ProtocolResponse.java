package cn.v7soft.admin.controller.resp;

import java.util.List;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.Protocol;
import cn.v7soft.dao.entities.primary.ProtocolTranslation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "协议信息响应")
public class ProtocolResponse extends IdResponse {

    private String name;
    private Long defaultLanguageId;
    private List<Language> languages;
    /**
     * 多语言版本列表
     */
    private List<ProtocolTranslationResponse> translations;

    public static ProtocolResponse convertEntity(Protocol protocol) {
        return ProtocolResponse.builder()
                .id(String.valueOf(protocol.getId()))
                .name(protocol.getName())
                .defaultLanguageId(protocol.getDefaultLanguage() == null ? null : protocol.getDefaultLanguage().getId())
                .languages(protocol.getTranslations().stream().map(ProtocolTranslation::getLanguage).toList())
                .translations(protocol.getTranslations().stream().map(ProtocolTranslationResponse::convertEntity).toList())
                .build();
    }
}
