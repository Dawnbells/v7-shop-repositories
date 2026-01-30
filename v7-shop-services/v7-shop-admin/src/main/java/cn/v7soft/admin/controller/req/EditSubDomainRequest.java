package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于编辑二级域名信息的请求类。
 */
@Getter
@Setter
public class EditSubDomainRequest extends IdRequest {
    @NotBlank(message = "二级域名记录不能为空")
    @Schema(title = "二级域名记录", example = "subdomain", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(title = "一级域名ID", example = "1")
    private Long parentDomainId;

    @Schema(title = "国家ID", example = "1")
    private Long countryId;
}
