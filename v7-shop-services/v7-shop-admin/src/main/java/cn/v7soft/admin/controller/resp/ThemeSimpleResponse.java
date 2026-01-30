package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.ThemeCustom;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 主题简单响应
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "主题简单信息响应")
public class ThemeSimpleResponse {

    @Schema(title = "主题ID")
    private String id;

    @Schema(title = "主题名称")
    private String name;

    public static ThemeSimpleResponse convertEntity(ThemeCustom theme) {
        if (theme == null) {
            return null;
        }
        return ThemeSimpleResponse.builder()
                .id(String.valueOf(theme.getId()))
                .name(theme.getName())
                .build();
    }
}

