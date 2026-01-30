package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于查询二级域名信息的请求类，支持分页。
 */
@Getter
@Setter
public class QuerySubDomainRequest extends BasePageRequest {
    @Positive
    @Schema(title = "所属一级域名ID", example = "1")
    private long parentId;
}
