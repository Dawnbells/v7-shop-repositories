package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryThemeCustomRequest extends BasePageRequest {
    @Schema(title = "自定义主题名称，模糊匹配")
    private String name;
}

