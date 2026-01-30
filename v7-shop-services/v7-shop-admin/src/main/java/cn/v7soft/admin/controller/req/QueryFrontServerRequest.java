package cn.v7soft.admin.controller.req;


import cn.v7soft.core.controller.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryFrontServerRequest extends BasePageRequest {

    @Schema(title = "服务器名称", example = "Server-1")
    private String name;

    @Schema(title = "最小有效解析数量", example = "50")
    private Integer minActiveResolutionCount;
}
