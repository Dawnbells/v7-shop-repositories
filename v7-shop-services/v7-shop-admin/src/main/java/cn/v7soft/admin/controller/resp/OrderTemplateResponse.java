package cn.v7soft.admin.controller.resp;

import java.util.List;
import java.util.stream.Collectors;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.OrderTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "订单下载模版响应")
public class OrderTemplateResponse extends DataRangeResponse {

    @Schema(title = "模版名称")
    private String templateName;

    @Schema(title = "是否是下载模板")
    private boolean downloadTemplate;

    @Schema(title = "表头配置")
    private List<OrderTemplateColumnResponse> columns;

    public static OrderTemplateResponse convertEntity(OrderTemplate entity) {
        if (entity == null) {
            return null;
        }
        return filling(entity, OrderTemplateResponse.builder()
                .templateName(entity.getTemplateName())
                .downloadTemplate(Boolean.TRUE.equals(entity.getDownloadTemplate()))
                .columns(entity.getColumns() == null ? null :
                         entity.getColumns().stream()
                                 .map(OrderTemplateColumnResponse::convertEntity)
                                 .collect(Collectors.toList()))
                .build());
    }
}
