package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "设置员工AI额度请求")
public class SetAiCreditsRequest {

    @NotNull(message = "员工ID不能为空")
    @Schema(title = "员工ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(title = "每月AI额度", description = "null/0=禁用, -1=无限制, >0=月度额度")
    @Min(value = -1, message = "额度不能小于-1")
    private Integer monthlyAiCredits;
}
