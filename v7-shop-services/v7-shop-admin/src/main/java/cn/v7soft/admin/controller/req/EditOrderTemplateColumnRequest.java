package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditOrderTemplateColumnRequest {
    @Schema(title = "id", example = "1")
    private Long id;
    @Schema(title = "表头名称", example = "订单ID")
    private String headerName;
    @Schema(title = "对应字段key", example = "id")
    private String fieldKey;
}
