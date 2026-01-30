package cn.v7soft.admin.controller.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TranslateProductRequest {
    @NotBlank(message = "产品ID")
    @Pattern(regexp = "^[0-9]+$", message = "转移用户ID不正确")
    private String productId;
    @NotBlank(message = "翻译的语言ID")
    @Pattern(regexp = "^[0-9]+$", message = "转移用户ID不正确")
    private String languageId;
}
