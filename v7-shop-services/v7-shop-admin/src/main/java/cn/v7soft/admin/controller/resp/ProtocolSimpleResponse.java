package cn.v7soft.admin.controller.resp;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Article;
import cn.v7soft.dao.entities.primary.Protocol;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;
import cn.v7soft.dao.entities.primary.ProtocolTranslation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "协议信息响应")
public class ProtocolSimpleResponse extends IdResponse {

    private String name;
    private Long defaultLanguageId;
    private Set<String> placeholders;

    public static ProtocolSimpleResponse convertEntity(Protocol protocol) {
        Pattern pattern = Pattern.compile("\\{\\{(.*?)}}");
        Set<String> placeholders = new HashSet<>();
        for (ProtocolTranslation translation : protocol.getTranslations()) {
            for (ProtocolArticleGroup protocolArticleGroup : translation.getArticleGroupList()) {
                for (Article article : protocolArticleGroup.getArticleList()) {
                    String title = article.getTitle();
                    String content = article.getContent();
                    matchAllPlaceholders(pattern, placeholders, article.getLanguage().getCode(), title);
                    matchAllPlaceholders(pattern, placeholders, article.getLanguage().getCode(), content);
                }
            }
        }

        return ProtocolSimpleResponse.builder()
                .id(String.valueOf(protocol.getId()))
                .name(protocol.getName())
                .defaultLanguageId(protocol.getDefaultLanguage() == null ? null : protocol.getDefaultLanguage().getId())
                .placeholders(placeholders)
                .build();
    }

    private static void matchAllPlaceholders(Pattern pattern, Set<String> placeholders, String languageCode, String text) {
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (placeholder != null && !placeholder.isEmpty()) {
                if (placeholder.startsWith("i18n_")) {
                    placeholders.add("(" + languageCode + ")" + placeholder.replace("i18n_", ""));
                } else {
                    placeholders.add(placeholder);
                }
            }
        }
    }
}
