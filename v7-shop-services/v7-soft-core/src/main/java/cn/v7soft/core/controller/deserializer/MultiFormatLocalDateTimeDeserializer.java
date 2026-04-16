package cn.v7soft.core.controller.deserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import cn.v7soft.core.enums.ClientResponseEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiFormatLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
            // 完整 ISO-8601 格式（带毫秒+时区，例如 2025-08-30T16:00:00.000Z）
            DateTimeFormatter.ISO_DATE_TIME,
            // 常见的本地时间格式
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateTimeStr = p.getText();
        try {
            long timeMillis = Long.parseLong(dateTimeStr);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault());
        } catch (Throwable ignored) {
        }

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_DATE_TIME) {
                    // 带时区的 ISO 格式，先用 OffsetDateTime 解析
                    OffsetDateTime odt = OffsetDateTime.parse(dateTimeStr, formatter);
                    // 转换到系统默认时区
                    return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                } else {
                    try {
                        return LocalDateTime.parse(dateTimeStr, formatter);
                    } catch (DateTimeParseException ignored) {
                    }
                    return LocalDate.parse(dateTimeStr, formatter).atStartOfDay();
                }
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个格式
            }
        }

        ClientResponseEnum.PARAMETER_ILLEGAL.throwException("Invalid date-time format: " + dateTimeStr);
        return null;
    }
}
