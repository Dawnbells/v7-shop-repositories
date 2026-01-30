package cn.v7soft.accountservice.controller.req;

import cn.v7soft.core.enums.StatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TreeSystemRouterRequest {
    @Schema(title = "查询状态", example = "VALID", requiredMode = Schema.RequiredMode.REQUIRED)
    private StatusEnum status;
}
