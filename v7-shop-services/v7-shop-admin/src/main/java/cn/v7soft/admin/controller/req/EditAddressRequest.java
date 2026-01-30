package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.entities.primary.Country;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditAddressRequest extends IdRequest {

    @NotBlank(message = "省份不能为空")
    @Schema(title = "省份", example = "北京市", requiredMode = Schema.RequiredMode.REQUIRED)
    private String province;

    @NotBlank(message = "城市不能为空")
    @Schema(title = "城市", example = "北京市", requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    @NotBlank(message = "区不能为空")
    @Schema(title = "区", example = "朝阳区", requiredMode = Schema.RequiredMode.REQUIRED)
    private String district;

    @NotBlank(message = "邮政编码不能为空")
    @Schema(title = "邮政编码", example = "100000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postalCode;

    @NotNull(message = "国家不能为空")
    @Schema(title = "国家", requiredMode = Schema.RequiredMode.REQUIRED)
    private Country country;
}
