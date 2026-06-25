package cn.v7soft.admin.statistics;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatisticsQueryFingerprintSerializerTest {

    @Test
    void forceRefreshDoesNotChangeSerializedFingerprintInput() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        SimpleModule module = new SimpleModule();
        module.addSerializer(
                OrderStatisticsQueryRequest.class,
                new OrderStatisticsQueryFingerprintSerializer()
        );
        mapper.registerModule(module);

        String normal = mapper.writeValueAsString(request(false));
        String refresh = mapper.writeValueAsString(request(true));

        assertThat(refresh).isEqualTo(normal);
        assertThat(refresh).doesNotContain("forceRefresh");
    }

    private OrderStatisticsQueryRequest request(boolean forceRefresh) {
        return OrderStatisticsQueryRequest.builder()
                .startDate(LocalDate.parse("2026-06-01"))
                .endDate(LocalDate.parse("2026-06-30"))
                .granularity(OrderStatisticsGranularity.DAY)
                .dimension(OrderStatisticsDimension.EMPLOYEE)
                .employeeIds(List.of("101"))
                .targetCurrencyCode("USD")
                .forceRefresh(forceRefresh)
                .build();
    }
}
