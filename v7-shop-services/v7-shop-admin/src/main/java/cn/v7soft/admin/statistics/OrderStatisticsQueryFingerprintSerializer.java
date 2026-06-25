package cn.v7soft.admin.statistics;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@JsonComponent
public class OrderStatisticsQueryFingerprintSerializer
        extends JsonSerializer<OrderStatisticsQueryRequest> {

    @Override
    public void serialize(
            OrderStatisticsQueryRequest request,
            JsonGenerator generator,
            SerializerProvider serializers
    ) throws IOException {
        generator.writeStartObject();
        generator.writeObjectField("startDate", request.getStartDate());
        generator.writeObjectField("endDate", request.getEndDate());
        generator.writeObjectField("granularity", request.getGranularity());
        generator.writeObjectField("dimension", request.getDimension());
        generator.writeObjectField("employeeIds", request.getEmployeeIds());
        generator.writeObjectField("departmentIds", request.getDepartmentIds());
        generator.writeObjectField("includeUnassigned", request.getIncludeUnassigned());
        generator.writeObjectField("platforms", request.getPlatforms());
        generator.writeObjectField("domains", request.getDomains());
        generator.writeObjectField("targetCurrencyCode", request.getTargetCurrencyCode());
        generator.writeObjectField(
                "temporaryExchangeRates",
                request.getTemporaryExchangeRates()
        );
        generator.writeEndObject();
    }
}
