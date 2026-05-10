package cn.v7soft.admin.controller.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiTranslateImageRequest {

    @NotBlank(message = "图片文件ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "图片文件ID格式不正确")
    private String multimediaFileId;

    @NotBlank(message = "目标语言ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "语言ID格式不正确")
    private String languageId;

    @Size(max = 2000, message = "Prompt长度不能超过2000字符")
    private String prompt;

    @NotBlank(message = "AI账号ID不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "AI账号ID格式不正确")
    private String aiAccountId;

    private String imageUrl;
}
