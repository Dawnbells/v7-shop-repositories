package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwitchOpenRequest  extends IdRequest {
    @Schema(title = "是否共享", example = "true", description = "共享状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean open;
}
