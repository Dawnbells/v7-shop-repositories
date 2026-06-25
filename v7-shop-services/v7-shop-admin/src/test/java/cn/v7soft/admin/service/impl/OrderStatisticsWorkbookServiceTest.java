package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsBucketResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatisticsWorkbookServiceTest {

    @Test
    void createsAllAggregateSheetsInFixedOrder() throws Exception {
        OrderStatisticsWorkbookService service =
                new OrderStatisticsWorkbookService();

        byte[] bytes = service.create(result());

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes)
        )) {
            assertThat(workbook.sheetIterator())
                    .toIterable()
                    .extracting(sheet -> sheet.getSheetName())
                    .containsExactly(
                            "查询说明",
                            "汇总",
                            "时间趋势",
                            "分组汇总",
                            "时间分组明细",
                            "原币汇总",
                            "汇率说明",
                            "缺失汇率"
                    );
        }
    }

    private OrderStatisticsResultResponse result() {
        OrderStatisticsMetricsResponse metrics =
                OrderStatisticsMetricsResponse.builder()
                        .orderCount(10)
                        .validOrderCount(8)
                        .invalidOrderCount(2)
                        .deliveredOrderCount(6)
                        .undeliveredOrderCount(2)
                        .deliveryRate("0.75")
                        .totalSalesAmount("100.00")
                        .invalidSalesAmount("10.00")
                        .undeliveredSalesAmount("20.00")
                        .deliveredSalesAmount("70.00")
                        .build();
        return OrderStatisticsResultResponse.builder()
                .targetCurrencyCode("USD")
                .summary(metrics)
                .buckets(List.of(OrderStatisticsBucketResponse.builder()
                        .key("2026-06-01")
                        .metrics(metrics)
                        .build()))
                .groups(List.of(OrderStatisticsGroupResponse.builder()
                        .groupKey("EMPLOYEE:101")
                        .id("101")
                        .name("Alice")
                        .metrics(metrics)
                        .build()))
                .originalCurrencies(List.of())
                .missingRates(List.of())
                .build();
    }
}
