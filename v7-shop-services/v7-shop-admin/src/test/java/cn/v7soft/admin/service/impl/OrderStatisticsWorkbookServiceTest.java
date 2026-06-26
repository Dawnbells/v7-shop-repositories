package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsBucketResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsBucketGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import org.apache.poi.ss.usermodel.CellType;
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

    @Test
    void writesBucketGroupDetailRows() throws Exception {
        OrderStatisticsWorkbookService service =
                new OrderStatisticsWorkbookService();

        byte[] bytes = service.create(result());

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes)
        )) {
            var sheet = workbook.getSheet("时间分组明细");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("2026-06-01");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("Alice");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue())
                    .isEqualTo(10D);
            // 签收率（列 7）：比例数值 + 百分比格式，而非文本
            var rate = sheet.getRow(1).getCell(7);
            assertThat(rate.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(rate.getNumericCellValue()).isEqualTo(0.75D);
            assertThat(rate.getCellStyle().getDataFormatString()).isEqualTo("0.00%");
            // 总销售额（列 8）：数值 + 金额格式，而非文本
            var total = sheet.getRow(1).getCell(8);
            assertThat(total.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(total.getNumericCellValue()).isEqualTo(100D);
            assertThat(total.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
        }
    }

    @Test
    void writesSummaryAmountsAndRateAsFormattedNumbers() throws Exception {
        OrderStatisticsWorkbookService service =
                new OrderStatisticsWorkbookService();

        byte[] bytes = service.create(result());

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes)
        )) {
            var row = workbook.getSheet("汇总").getRow(1); // “全部”行
            // 列：0范围,1订单数,...,6签收率,7总销售额,8无效销售额,9未签收销售额,10签收销售额
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(10D);
            var rate = row.getCell(6);
            assertThat(rate.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(rate.getNumericCellValue()).isEqualTo(0.75D);
            assertThat(rate.getCellStyle().getDataFormatString()).isEqualTo("0.00%");
            var total = row.getCell(7);
            assertThat(total.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(total.getNumericCellValue()).isEqualTo(100D);
            assertThat(total.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
        }
    }
    @Test
    void writesGeneratedAtInUserTimeZone() throws Exception {
        OrderStatisticsResultResponse result = result();
        result.setGeneratedAt("2026-06-25T00:00:00Z");
        result.setTimeZoneId("Asia/Shanghai");

        byte[] bytes = new OrderStatisticsWorkbookService().create(result);

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes)
        )) {
            var sheet = workbook.getSheet("查询说明");
            // Asia/Shanghai = UTC+8 → 00:00Z 即 08:00
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("2026-06-25 08:00:00 (Asia/Shanghai)");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue())
                    .isEqualTo("Asia/Shanghai");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue())
                    .isEqualTo("Asia/Shanghai");
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
                .bucketGroups(List.of(OrderStatisticsBucketGroupResponse.builder()
                        .bucketKey("2026-06-01")
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
