package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 复制员工名下全部 SPU 给指定员工的请求。
 */
@Getter
@Setter
public class CopyEmployeeSpuRequest {

    @NotBlank(message = "源员工不允许为空")
    @Pattern(regexp = "^[0-9]+$", message = "源员工ID不正确")
    @Schema(title = "源员工ID", description = "被复制商品的员工ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceUserId;

    @NotBlank(message = "目标员工不允许为空")
    @Pattern(regexp = "^[0-9]+$", message = "目标员工ID不正确")
    @Schema(title = "目标员工ID", description = "接收复制商品的员工ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetUserId;
}
