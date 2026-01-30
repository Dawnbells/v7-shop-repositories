package cn.v7soft.admin.controller.req;

import cn.hutool.json.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDynamicConfigRequest {

    @NotBlank(message = "配置名称不能为空")
    @Schema(title = "配置名称", example = "email-settings")
    private String configName;

    @Schema(title = "部门ID", description = "为空表示公司级别配置，有值表示部门级别配置")
    private Long departmentId;

    @NotNull(message = "配置值不能为空")
    @Schema(title = "配置值")
    private JSONObject configValue;
}

