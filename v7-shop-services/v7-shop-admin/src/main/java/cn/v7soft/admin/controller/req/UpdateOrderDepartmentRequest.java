package cn.v7soft.admin.controller.req;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderDepartmentRequest {
    @NotEmpty(message = "请选择订单")
    private List<@NotNull @Positive Long> ids;

    @NotBlank(message = "请输入部门名称")
    @Size(max = 100, message = "部门名称不能超过100个字符")
    private String department;
}
