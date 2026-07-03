package cn.v7soft.admin.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传单元格取值：渠道/仓库以表头为准——表头出现即视为该格存在，
 * 空单元格（含行尾被 xlsx 裁剪读不到的格子）统一按空串读出；其他字段维持原行为。
 */
class TaskExecutorServiceUploadCellTest {

    private static final Map<String, String> ALIAS = Map.of(
            "remarks", "备注",
            "deliveryChannel", "渠道",
            "storehouse", "仓库");

    private static final Map<String, Integer> HEADER = Map.of(
            "备注", 0,
            "渠道", 1,
            "仓库", 2);

    @Test
    void headerAbsentTreatsFieldAsMissing() {
        Map<String, Integer> header = Map.of("备注", 0);
        List<?> rowCells = List.of("备注值");
        assertThat(TaskExecutorService.hasUploadCell(rowCells, header, ALIAS, "deliveryChannel")).isFalse();
        assertThat(TaskExecutorService.getUploadCellValue(rowCells, header, ALIAS, "deliveryChannel")).isNull();
        assertThat(TaskExecutorService.hasUploadCell(rowCells, header, ALIAS, "storehouse")).isFalse();
    }

    @Test
    void truncatedTrailingCellsReadAsBlankForClearOnBlankKeys() {
        List<?> rowCells = List.of("备注值"); // 行尾渠道/仓库为空，未写入 xlsx，行被裁剪
        assertThat(TaskExecutorService.hasUploadCell(rowCells, HEADER, ALIAS, "deliveryChannel")).isTrue();
        assertThat(TaskExecutorService.getUploadCellValue(rowCells, HEADER, ALIAS, "deliveryChannel")).isEqualTo("");
        assertThat(TaskExecutorService.hasUploadCell(rowCells, HEADER, ALIAS, "storehouse")).isTrue();
        assertThat(TaskExecutorService.getUploadCellValue(rowCells, HEADER, ALIAS, "storehouse")).isEqualTo("");
    }

    @Test
    void truncatedTrailingCellsStillSkipOtherFields() {
        Map<String, Integer> header = Map.of("渠道", 0, "备注", 1);
        List<?> rowCells = List.of("云途"); // 行尾备注被裁剪：其他字段维持"视为列不存在"
        assertThat(TaskExecutorService.hasUploadCell(rowCells, header, ALIAS, "remarks")).isFalse();
        assertThat(TaskExecutorService.getUploadCellValue(rowCells, header, ALIAS, "remarks")).isNull();
    }

    @Test
    void readsCellValuesAndNormalizesNullToBlankForClearOnBlankKeys() {
        List<?> rowCells = Arrays.asList("备注值", " 云途 ", null);
        assertThat(TaskExecutorService.getUploadCellValue(rowCells, HEADER, ALIAS, "remarks")).isEqualTo("备注值");
        assertThat(TaskExecutorService.getUploadCellValue(rowCells, HEADER, ALIAS, "deliveryChannel")).isEqualTo(" 云途 ");
        assertThat(TaskExecutorService.getUploadCellValue(rowCells, HEADER, ALIAS, "storehouse")).isEqualTo("");
    }
}
