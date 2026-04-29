package cn.v7soft.admin.controller.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TranslateByAIRequest {
    @NotBlank(message = "产品ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "产品ID格式不正确")
    private String productId;

    @NotBlank(message = "目标国家ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "国家ID格式不正确")
    private String countryId;

    @NotBlank(message = "目标语言ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "语言ID格式不正确")
    private String languageId;

    private Integer totalRequests;
}
