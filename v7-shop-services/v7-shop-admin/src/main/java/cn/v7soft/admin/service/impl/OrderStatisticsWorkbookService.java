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
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OrderStatisticsWorkbookService {

    private static final String RAW_AMOUNT_FORMAT = "#,##0.########";
    private static final String PERCENT_FORMAT = "0.00%";
    private static final String DATABASE_TIME_ZONE = "Asia/Shanghai";
    private static final DateTimeFormatter GENERATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] create(OrderStatisticsResultResponse result) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = createStyles(workbook, inferFractionDigits(result));
            writeQueryInfo(workbook, result, styles);
            writeSummary(workbook, result, styles);
            writeBuckets(workbook, result, styles);
            writeGroups(workbook, result, styles);
            writeBucketGroups(workbook, result, styles);
            writeOriginalCurrencies(workbook, result, styles);
            writeRateInfo(workbook, result, styles);
            writeMissingRates(workbook, result, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("订单统计 Excel 生成失败", exception);
        }
    }

    private void writeQueryInfo(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("查询说明");
        headerRow(sheet, 0, styles, "项目", "内容");
        row(sheet, 1, styles, "生成时间", formatGeneratedAt(result));
        row(sheet, 2, styles, "用户时区", resolveZoneId(result));
        row(sheet, 3, styles, "数据库时间基准", DATABASE_TIME_ZONE);
        row(sheet, 4, styles, "目标币种", result.getTargetCurrencyCode());
        row(sheet, 5, styles, "数据口径", "订单当前状态的聚合快照");
        autoSize(sheet, 2);
    }

    /** 按用户时区格式化生成时间；老快照/空值兜底，绝不抛 NPE。 */
    private String formatGeneratedAt(OrderStatisticsResultResponse result) {
        ZoneId zone = parseZone(result == null ? null : result.getTimeZoneId());
        Instant instant = parseInstant(result == null ? null : result.getGeneratedAt());
        return GENERATED_AT_FORMAT.withZone(zone).format(instant) + " (" + zone.getId() + ")";
    }

    private String resolveZoneId(OrderStatisticsResultResponse result) {
        return parseZone(result == null ? null : result.getTimeZoneId()).getId();
    }

    private ZoneId parseZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zoneId);
        } catch (RuntimeException ignored) {
            return ZoneOffset.UTC;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }

    private void writeSummary(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("汇总");
        writeMetricsHeader(sheet, styles);
        writeMetrics(sheet, 1, "全部", result.getSummary(), styles);
        autoSize(sheet, 12);
    }

    private void writeBuckets(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("时间趋势");
        writeMetricsHeader(sheet, styles);
        int index = 1;
        for (OrderStatisticsBucketResponse bucket : safe(result.getBuckets())) {
            writeMetrics(sheet, index++, bucket.getKey(), bucket.getMetrics(), styles);
        }
        autoSize(sheet, 12);
    }

    private void writeGroups(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("分组汇总");
        writeMetricsHeader(sheet, styles);
        int index = 1;
        for (OrderStatisticsGroupResponse group : safe(result.getGroups())) {
            writeMetrics(
                    sheet,
                    index++,
                    group.getName() + (group.isHistorical() ? "（历史）" : ""),
                    group.getMetrics(),
                    styles
            );
        }
        autoSize(sheet, 12);
    }

    private void writeBucketGroups(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("时间分组明细");
        headerRow(
                sheet,
                0,
                styles,
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
                row(sheet, index++, styles, item.getBucketKey(), item.getName());
                continue;
            }
            row(
                    sheet,
                    index++,
                    styles,
                    item.getBucketKey(),
                    item.getName() + (item.isHistorical() ? "（历史）" : ""),
                    metrics.getOrderCount(),
                    metrics.getValidOrderCount(),
                    metrics.getInvalidOrderCount(),
                    metrics.getDeliveredOrderCount(),
                    metrics.getUndeliveredOrderCount(),
                    new Percent(metrics.getDeliveryRate()),
                    new Amount(metrics.getTotalSalesAmount()),
                    new Amount(metrics.getInvalidSalesAmount()),
                    new Amount(metrics.getUndeliveredSalesAmount()),
                    new Amount(metrics.getDeliveredSalesAmount()),
                    metrics.getMissingRateOrderCount()
            );
        }
        autoSize(sheet, 13);
    }

    private void writeOriginalCurrencies(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("原币汇总");
        headerRow(
                sheet,
                0,
                styles,
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
                    styles,
                    item.getCurrencyCode(),
                    item.getOrderCount(),
                    new RawAmount(item.getTotalAmount()),
                    new RawAmount(item.getInvalidAmount()),
                    new RawAmount(item.getUndeliveredAmount()),
                    new RawAmount(item.getDeliveredAmount())
            );
        }
        autoSize(sheet, 6);
    }

    private void writeRateInfo(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("汇率说明");
        headerRow(sheet, 0, styles, "项目", "内容");
        row(sheet, 1, styles, "目标币种", result.getTargetCurrencyCode());
        row(
                sheet,
                2,
                styles,
                "优先级",
                "查询临时汇率 > 个人汇率 > 订单历史汇率 > 系统汇率"
        );
        row(sheet, 3, styles, "计算精度", "BigDecimal 高精度聚合后统一舍入");
        autoSize(sheet, 2);
    }

    private void writeMissingRates(
            XSSFWorkbook workbook,
            OrderStatisticsResultResponse result,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet("缺失汇率");
        headerRow(sheet, 0, styles, "币种", "原因", "订单数", "原币金额");
        int index = 1;
        for (OrderStatisticsMissingRateResponse item
                : safe(result.getMissingRates())) {
            row(
                    sheet,
                    index++,
                    styles,
                    item.getCurrencyCode(),
                    item.getReason(),
                    item.getOrderCount(),
                    new RawAmount(item.getOriginalAmount())
            );
        }
        autoSize(sheet, 4);
    }

    private void writeMetricsHeader(Sheet sheet, Styles styles) {
        headerRow(
                sheet,
                0,
                styles,
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
            OrderStatisticsMetricsResponse metrics,
            Styles styles
    ) {
        if (metrics == null) {
            row(sheet, rowIndex, styles, label);
            return;
        }
        row(
                sheet,
                rowIndex,
                styles,
                label,
                metrics.getOrderCount(),
                metrics.getValidOrderCount(),
                metrics.getInvalidOrderCount(),
                metrics.getDeliveredOrderCount(),
                metrics.getUndeliveredOrderCount(),
                new Percent(metrics.getDeliveryRate()),
                new Amount(metrics.getTotalSalesAmount()),
                new Amount(metrics.getInvalidSalesAmount()),
                new Amount(metrics.getUndeliveredSalesAmount()),
                new Amount(metrics.getDeliveredSalesAmount()),
                metrics.getMissingRateOrderCount()
        );
    }

    /**
     * 表头行：整行加粗。
     */
    private void headerRow(Sheet sheet, int rowIndex, Styles styles, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(values[index] == null ? "" : String.valueOf(values[index]));
            cell.setCellStyle(styles.header());
        }
    }

    /**
     * 数据行：按值类型写入。金额/签收率作为数值 + 数字/百分比格式（规格 §3.1 / §8.6），
     * 而非文本——保证 Excel 可求和、可格式化显示。
     */
    private void row(Sheet sheet, int rowIndex, Styles styles, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            writeCell(row.createCell(index), values[index], styles);
        }
    }

    private void writeCell(Cell cell, Object value, Styles styles) {
        if (value instanceof Amount amount) {
            writeDecimal(cell, amount.value(), styles.amount());
        } else if (value instanceof RawAmount rawAmount) {
            writeDecimal(cell, rawAmount.value(), styles.rawAmount());
        } else if (value instanceof Percent percent) {
            writeDecimal(cell, percent.value(), styles.percent());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
    }

    private void writeDecimal(Cell cell, String value, CellStyle style) {
        if (value == null || value.isBlank()) {
            // 空值（如无有效订单时签收率返回 null）保持空单元格
            return;
        }
        try {
            cell.setCellValue(new BigDecimal(value).doubleValue());
            cell.setCellStyle(style);
        } catch (NumberFormatException exception) {
            // 非法数值兜底为文本，避免单元格异常导致整个导出失败
            cell.setCellValue(value);
        }
    }

    private Styles createStyles(XSSFWorkbook workbook, int fractionDigits) {
        DataFormat format = workbook.createDataFormat();
        CellStyle header = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        header.setFont(font);
        CellStyle amount = workbook.createCellStyle();
        amount.setDataFormat(format.getFormat(amountPattern(fractionDigits)));
        CellStyle rawAmount = workbook.createCellStyle();
        rawAmount.setDataFormat(format.getFormat(RAW_AMOUNT_FORMAT));
        CellStyle percent = workbook.createCellStyle();
        percent.setDataFormat(format.getFormat(PERCENT_FORMAT));
        return new Styles(header, amount, rawAmount, percent);
    }

    /**
     * 目标币种金额的数字格式：千分位 + 目标币种 fractionDigits 位小数。
     */
    private String amountPattern(int fractionDigits) {
        if (fractionDigits <= 0) {
            return "#,##0";
        }
        StringBuilder pattern = new StringBuilder("#,##0.");
        for (int index = 0; index < fractionDigits; index++) {
            pattern.append('0');
        }
        return pattern.toString();
    }

    /**
     * 从汇总总销售额字符串推断目标币种 fractionDigits（后端按 fractionDigits setScale，
     * 小数位数即 fractionDigits），避免额外携带币种精度字段。
     */
    private int inferFractionDigits(OrderStatisticsResultResponse result) {
        OrderStatisticsMetricsResponse summary = result.getSummary();
        String amount = summary == null ? null : summary.getTotalSalesAmount();
        if (amount == null) {
            return 2;
        }
        int dot = amount.indexOf('.');
        return dot < 0 ? 0 : amount.length() - dot - 1;
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private <T> List<T> safe(List<T> items) {
        return items == null ? List.of() : items;
    }

    private record Styles(CellStyle header, CellStyle amount, CellStyle rawAmount, CellStyle percent) {
    }

    /** 目标币种统一换算后的销售额，写为数值 + 目标币种金额格式。 */
    private record Amount(String value) {
    }

    /** 原始币种金额，写为数值 + 通用金额格式（保留原始小数）。 */
    private record RawAmount(String value) {
    }

    /** 比例值（签收率），写为比例数值 + 百分比格式。 */
    private record Percent(String value) {
    }
}
