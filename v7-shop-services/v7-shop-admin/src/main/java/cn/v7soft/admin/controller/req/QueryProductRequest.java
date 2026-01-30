package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于查询商品信息的请求类，支持分页。
 */
@Getter
@Setter
public class QueryProductRequest extends BasePageRequest {
    @Schema(title = "商品标题", example = "高端智能手机")
    private String title;

    @Schema(title = "是否收取税费", example = "true")
    private Boolean isTaxable;

    @Schema(title = "是否是多规格", example = "true")
    private Boolean isMultiSpecs;
}
