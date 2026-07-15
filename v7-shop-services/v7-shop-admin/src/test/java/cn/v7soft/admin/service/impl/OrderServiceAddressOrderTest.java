package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.dto.OrderDownloadDto;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.enums.AddressOrder;
import cn.v7soft.dao.enums.CheckStatus;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.enums.PaymentMethod;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceAddressOrderTest {

    @Test
    void convertsMixedCountryPageWithOneAddressOrderLookup() {
        ICountryService countryService = mock(ICountryService.class);
        OrderService service = new OrderService(
                mock(OrderRepository.class),
                mock(AsyncTaskRepository.class),
                mock(ITaskExecutorService.class),
                mock(TemporaryOrderRepository.class),
                countryService
        );
        when(countryService.getAddressOrders(Set.of("TW", "DE")))
                .thenReturn(Map.of(
                        "TW", AddressOrder.FORWARD,
                        "DE", AddressOrder.REVERSE
                ));

        Page<OrderDownloadDto> result = service.convertDownloadPage(
                new PageImpl<>(List.of(order("TW"), order("DE")))
        );

        assertThat(result.map(OrderDownloadDto::getFullAddress).getContent())
                .containsExactly(
                        "台湾省 台北市 中正區 忠孝西路100号",
                        "忠孝西路100号 中正區 台北市 台湾省"
                );
        verify(countryService).getAddressOrders(Set.of("TW", "DE"));
    }

    private Order order(String countryCode) {
        return Order.builder()
                .contextInfo(OrderContextInfo.builder()
                        .currencyFractionDigits(2)
                        .country(countryCode)
                        .countryCode(countryCode)
                        .build())
                .deliveryInfo(OrderDeliveryInfo.builder()
                        .firstName("测试")
                        .lastName("")
                        .phone("123456")
                        .province("台湾省")
                        .city("台北市")
                        .district("中正區")
                        .address("忠孝西路100号")
                        .build())
                .financialInfo(OrderFinancialInfo.builder()
                        .totalAmount(BigDecimal.TEN)
                        .build())
                .paymentInfo(OrderPaymentInfo.builder()
                        .paymentMethod(PaymentMethod.COD)
                        .build())
                .itemInfos(List.of())
                .orderTime(LocalDateTime.of(2026, 7, 14, 12, 0))
                .importTime(LocalDateTime.of(1970, 1, 1, 0, 0))
                .orderStatus(OrderStatus.PENDING)
                .botOrderStatus(CheckStatus.PENDING)
                .build();
    }
}
