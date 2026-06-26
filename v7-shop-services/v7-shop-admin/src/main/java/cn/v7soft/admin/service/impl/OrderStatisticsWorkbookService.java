package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsBucketResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsBucketGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsMissingRateResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsOriginalCurrencyResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class OrderStatisticsWorkbookService {

    public byte[] create(OrderStatisticsResultResponse result) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            writeQueryInfo(workbook, result, headerStyle);
            writeSummary(workbook, result, headerStyle);
            writeBuckets(workbook, result, headerStyle);
            writeGroups(workbook, result, headerStyle);
            writeBucketGroups(workbook, result, headerStyle);
            writeOriginalCurrencies(workbook, result, headerStyle);
            writeRateInfo(workbook, result, headerStyle);
            writeMissingRates(workbook, result, headerStyle);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("订单统计 Excel 生成失败", exception);
        }
    }

    private void writeQueryInfo(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("查询说明");
        row(sheet, 0, headerStyle, "项目", "内容");
        row(sheet, 1, null, "生成时间", Instant.now().toString());
        row(sheet, 2, null, "目标币种", result.getTargetCurrencyCode());
        row(sheet, 3, null, "数据口径", "订单当前状态的聚合快照");
        autoSize(sheet, 2);
    }

    private void writeSummary(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("汇总");
        writeMetricsHeader(sheet, headerStyle);
        writeMetrics(sheet, 1, "全部", result.getSummary());
        autoSize(sheet, 12);
    }

    private void writeBuckets(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("时间趋势");
        writeMetricsHeader(sheet, headerStyle);
        int index = 1;
        for (OrderStatisticsBucketResponse bucket : safe(result.getBuckets())) {
            writeMetrics(sheet, index++, bucket.getKey(), bucket.getMetrics());
        }
        autoSize(sheet, 12);
    }

    private void writeGroups(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("分组汇总");
        writeMetricsHeader(sheet, headerStyle);
        int index = 1;
        for (OrderStatisticsGroupResponse group : safe(result.getGroups())) {
            writeMetrics(
                    sheet,
                    index++,
                    group.getName() + (group.isHistorical() ? "（历史）" : ""),
                    group.getMetrics()
            );
        }
        autoSize(sheet, 12);
    }

    private void writeBucketGroups(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("时间分组明细");
        row(
                sheet,
                0,
                headerStyle,
                "时间桶",
                "分组",
                "订单数",
                "有效订单数",
                "无效订单数",
                "签收订单数",
                "未签收订单数",
                "签收率",
                "总销售额",
                "无效销售额",
                "未签收销售额",
                "签收销售额",
                "缺失汇率订单数"
        );
        int index = 1;
        for (OrderStatisticsBucketGroupResponse item : safe(result.getBucketGroups())) {
            OrderStatisticsMetricsResponse metrics = item.getMetrics();
            if (metrics == null) {
                row(sheet, index++, null, item.getBucketKey(), item.getName());
                continue;
            }
            row(
                    sheet,
                    index++,
                    null,
                    item.getBucketKey(),
                    item.getName() + (item.isHistorical() ? "（历史）" : ""),
                    metrics.getOrderCount(),
                    metrics.getValidOrderCount(),
                    metrics.getInvalidOrderCount(),
                    metrics.getDeliveredOrderCount(),
                    metrics.getUndeliveredOrderCount(),
                    metrics.getDeliveryRate(),
                    metrics.getTotalSalesAmount(),
                    metrics.getInvalidSalesAmount(),
                    metrics.getUndeliveredSalesAmount(),
                    metrics.getDeliveredSalesAmount(),
                    metrics.getMissingRateOrderCount()
            );
        }
        autoSize(sheet, 13);
    }

    private void writeOriginalCurrencies(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("原币汇总");
        row(
                sheet,
                0,
                headerStyle,
                "币种",
                "订单数",
                "总金额",
                "无效金额",
                "未签收金额",
                "签收金额"
        );
        int index = 1;
        for (OrderStatisticsOriginalCurrencyResponse item
                : safe(result.getOriginalCurrencies())) {
            row(
                    sheet,
                    index++,
                    null,
                    item.getCurrencyCode(),
                    item.getOrderCount(),
                    item.getTotalAmount(),
                    item.getInvalidAmount(),
                    item.getUndeliveredAmount(),
                    item.getDeliveredAmount()
            );
        }
        autoSize(sheet, 6);
    }

    private void writeRateInfo(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("汇率说明");
        row(sheet, 0, headerStyle, "项目", "内容");
        row(sheet, 1, null, "目标币种", result.getTargetCurrencyCode());
        row(
                sheet,
                2,
                null,
                "优先级",
                "查询临时汇率 > 个人汇率 > 订单历史汇率 > 系统汇率"
        );
        row(sheet, 3, null, "计算精度", "BigDecimal 高精度聚合后统一舍入");
        autoSize(sheet, 2);
    }

    private void writeMissingRates(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("缺失汇率");
        row(sheet, 0, headerStyle, "币种", "原因", "订单数", "原币金额");
        int index = 1;
        for (OrderStatisticsMissingRateResponse item
                : safe(result.getMissingRates())) {
            row(
                    sheet,
                    index++,
                    null,
                    item.getCurrencyCode(),
                    item.getReason(),
                    item.getOrderCount(),
                    item.getOriginalAmount()
            );
        }
        autoSize(sheet, 4);
    }

    private void writeMetricsHeader(Sheet sheet, CellStyle headerStyle) {
        row(
                sheet,
                0,
                headerStyle,
                "范围",
                "订单数",
                "有效订单数",
                "无效订单数",
                "签收订单数",
                "未签收订单数",
                "签收率",
                "总销售额",
                "无效销售额",
                "未签收销售额",
                "签收销售额",
                "缺失汇率订单数"
        );
    }

    private void writeMetrics(
            Sheet sheet,
            int rowIndex,
            String label,
            OrderStatisticsMetricsResponse metrics
    ) {
        if (metrics == null) {
            row(sheet, rowIndex, null, label);
            return;
        }
        row(
                sheet,
                rowIndex,
                null,
                label,
                metrics.getOrderCount(),
                metrics.getValidOrderCount(),
                metrics.getInvalidOrderCount(),
                metrics.getDeliveredOrderCount(),
                metrics.getUndeliveredOrderCount(),
                metrics.getDeliveryRate(),
                metrics.getTotalSalesAmount(),
                metrics.getInvalidSalesAmount(),
                metrics.getUndeliveredSalesAmount(),
                metrics.getDeliveredSalesAmount(),
                metrics.getMissingRateOrderCount()
        );
    }

    private void row(
            Sheet sheet,
            int rowIndex,
            CellStyle style,
            Object... values
    ) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            Object value = values[index];
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : String.valueOf(value));
            }
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private <T> List<T> safe(List<T> items) {
        return items == null ? List.of() : items;
    }
}
