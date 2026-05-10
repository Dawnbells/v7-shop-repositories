package cn.v7soft.admin.controller.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiTranslateTextRequest {

    @NotBlank(message = "翻译文本不能为空")
    @Size(max = 10000, message = "文本长度不能超过10000字符")
    private String text;

    @NotBlank(message = "目标语言ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "语言ID格式不正确")
    private String languageId;

    @Size(max = 2000, message = "Prompt长度不能超过2000字符")
    private String prompt;

    @NotBlank(message = "AI账号ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "AI账号ID格式不正确")
    private String aiAccountId;
}
