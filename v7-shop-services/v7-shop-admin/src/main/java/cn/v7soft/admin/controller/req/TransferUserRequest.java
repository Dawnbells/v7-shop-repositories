package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferUserRequest extends IdRequest {
    @NotBlank(message = "转移用户不允许为空")
    @Pattern(regexp = "^[0-9]+$", message = "转移用户ID不正确")
    @Schema(title = "转移用户ID", example = "1", description = "转移的用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String transferUserId;
}
