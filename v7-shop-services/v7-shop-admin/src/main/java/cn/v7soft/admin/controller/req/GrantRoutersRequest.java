package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GrantRoutersRequest extends IdRequest {
    @Schema(title = "授予的路由列表", example = "[1, 2]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> routerIds;
}
