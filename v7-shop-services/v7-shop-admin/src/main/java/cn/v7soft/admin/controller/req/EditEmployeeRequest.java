package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditEmployeeRequest extends IdRequest {
    @NotBlank(message = "姓名不能为空")
    @Schema(title = "姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "性别不能为空")
    @Schema(title = "性别", example = "MALE", requiredMode = Schema.RequiredMode.REQUIRED)
    private Gender gender;

    @NotBlank(message = "电话号码不能为空")
    @Schema(title = "电话号码", example = "13812345678", requiredMode = Schema.RequiredMode.REQUIRED)
    private String telephone;

    @NotBlank(message = "密码不能为空")
    @Schema(title = "密码", example = "Abc123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
