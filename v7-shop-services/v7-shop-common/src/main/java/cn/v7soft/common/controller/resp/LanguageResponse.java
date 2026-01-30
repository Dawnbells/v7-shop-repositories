package cn.v7soft.common.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "语言信息响应")
public class LanguageResponse extends IdResponse {
    @Schema(title = "语言名称", example = "English")
    private String name;

    @Schema(title = "语言中文名称", example = "英语")
    private String cname;

    @Schema(title = "语言代码", example = "en")
    private String code;

    public static LanguageResponse convertEntity(Language language) {
        if(language == null) {
            return null;
        }
        return filling(language, LanguageResponse.builder()
                .name(language.getName())
                .cname(language.getCname())
                .code(language.getCode())
                .build());
    }
}