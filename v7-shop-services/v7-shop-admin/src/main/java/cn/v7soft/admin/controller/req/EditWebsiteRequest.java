package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于编辑网站信息的请求类。
 */
@Getter
@Setter
public class EditWebsiteRequest extends IdRequest {
    @NotBlank(message = "网站名称不能为空")
    @Schema(title = "网站名称", example = "我的商城", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(title = "国家ID", example = "1")
    private Long countryId;
}
