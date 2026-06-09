package cn.v7soft.admin.controller.req;

import cn.hutool.json.JSONObject;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SaveOrderSearchPresetRequest {

    @NotNull(message = "页面类型不能为空")
    @Schema(title = "页面类型", example = "ORDER_AUDIT")
    private OrderSearchPresetPageType pageType;

    @NotBlank(message = "条件名称不能为空")
    @Size(max = 50, message = "条件名称不能超过50个字符")
    @Schema(title = "条件名称")
    private String name;

    @NotNull(message = "时间保存方式不能为空")
    @Schema(title = "时间保存方式", example = "RELATIVE")
    private OrderSearchPresetTimeMode timeMode;

    @Schema(title = "搜索条件JSON")
    private JSONObject queryParams;
}
