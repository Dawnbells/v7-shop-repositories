package cn.v7soft.admin.controller.resp;

import cn.hutool.json.JSONObject;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
@Schema(description = "订单搜索条件预设")
public class OrderSearchPresetResponse extends IdResponse {

    private String name;
    private OrderSearchPresetPageType pageType;
    private OrderSearchPresetTimeMode timeMode;
    private JSONObject queryParams;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;

    public static OrderSearchPresetResponse convertEntity(OrderSearchPreset entity) {
        return filling(entity, OrderSearchPresetResponse.builder()
                .name(entity.getName())
                .pageType(entity.getPageType())
                .timeMode(entity.getTimeMode())
                .queryParams(entity.getQueryParams())
                .lastUsedTime(entity.getLastUsedTime())
                .createTime(entity.getCreateTime())
                .build());
    }
}
