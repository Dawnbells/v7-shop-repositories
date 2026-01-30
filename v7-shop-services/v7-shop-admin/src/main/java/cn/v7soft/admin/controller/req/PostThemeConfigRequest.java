package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PostThemeConfigRequest extends IdRequest {
    private String base;
    private String i18n;
    private String template;
    private String theme;
}
