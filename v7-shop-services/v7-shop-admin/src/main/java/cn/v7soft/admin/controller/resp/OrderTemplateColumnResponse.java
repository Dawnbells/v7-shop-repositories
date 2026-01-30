package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.OrderTemplateColumn;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "订单模版表头配置响应")
public class OrderTemplateColumnResponse {

    @Schema(title = "表头配置ID")
    private Long id;

    @Schema(title = "表头显示名称")
    private String headerName;

    @Schema(title = "对应字段key")
    private String fieldKey;

    public static OrderTemplateColumnResponse convertEntity(OrderTemplateColumn entity) {
        if (entity == null) {
            return null;
        }
        return OrderTemplateColumnResponse.builder()
                .id(entity.getId())
                .headerName(entity.getHeaderName())
                .fieldKey(entity.getFieldKey())
                .build();
    }
}
