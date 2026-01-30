package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShareSpuRequest extends IdRequest {
    @NotBlank(message = "分享用户不允许为空")
    @Pattern(regexp = "^[0-9]+$", message = "分享用户ID不正确")
    @Schema(title = "分享用户ID", example = "1", description = "分享的用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetUserId;
}
