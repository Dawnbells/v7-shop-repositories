package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "订单统计查询请求")
public class OrderStatisticsQueryRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    private OrderStatisticsGranularity granularity;

    private OrderStatisticsDimension dimension;

    @Builder.Default
    private List<String> employeeIds = List.of();

    @Builder.Default
    private List<String> departmentIds = List.of();

    @Builder.Default
    private Boolean includeUnassigned = false;

    @Schema(description = "全部模式：不限员工/部门，统计数据权限范围内的所有订单"
            + "（含归属已失效、未归属），口径等同订单管理")
    @Builder.Default
    private Boolean selectAll = false;

    @Builder.Default
    private List<WebsiteTypeEnum> platforms = List.of();

    @Builder.Default
    private List<String> domains = List.of();

    private String targetCurrencyCode;

    @Builder.Default
    private Map<String, String> temporaryExchangeRates = new LinkedHashMap<>();

    @Builder.Default
    private Boolean forceRefresh = false;
}
