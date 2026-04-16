package cn.v7soft.core.controller.request;

import cn.v7soft.core.enums.StatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class SwitchValidityRequest extends IdRequest {

    @NotNull
    @Schema(title = "状态", example = "INVALID", description = "要切换的状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private StatusEnum status;
}
