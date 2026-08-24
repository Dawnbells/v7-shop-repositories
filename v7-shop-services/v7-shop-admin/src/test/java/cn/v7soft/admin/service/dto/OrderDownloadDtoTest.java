package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.enums.CheckStatus;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import cn.v7soft.dao.entities.primary.OrderTemplateColumn;
import cn.v7soft.dao.enums.AddressOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDownloadDtoTest {

    @Test
    void buildsFullAddressInForwardOrder() {
        assertThat(OrderDownloadDto.buildFullAddress(
                AddressOrder.FORWARD,
                "台湾省/Taiwan",
                "台北市/Taipei",
                "中正區/Zhongzheng",
                "忠孝西路100号"
        )).isEqualTo("台湾省 台北市 中正區 忠孝西路100号");
    }

    @Test
    void skipsBlankAddressPartsWithoutExtraSpaces() {
        assertThat(OrderDownloadDto.buildFullAddress(
                AddressOrder.FORWARD,
                "台湾省",
                null,
                " ",
                "忠孝西路100号"
        )).isEqualTo("台湾省 忠孝西路100号");
    }

    @Test
    void convertsFullAddressUsingProvidedOrder() {
        OrderDownloadDto dto = OrderDownloadDto.convert(order(), AddressOrder.FORWARD);

        assertThat(dto.getFullAddress())
                .isEqualTo("台湾省 台北市 中正區 忠孝西路100号");
    }

    @Test
    void usesOrderSkuSummaryForDownloadedSkuCode() {
        Order order = order();
        order.setSkuCodes("SKU-Ax2+SKU-B");
        order.setItemInfos(List.of(
                OrderItemInfo.builder()
                        .skuCode("SKU-A")
                        .quantity(2L)
                        .sellPrice(BigDecimal.ONE)
                        .build(),
                OrderItemInfo.builder()
                        .skuCode("SKU-B")
                        .quantity(1L)
                        .sellPrice(BigDecimal.ONE)
                        .build()
        ));

        OrderDownloadDto dto = OrderDownloadDto.convert(order, AddressOrder.FORWARD);

        assertThat(dto.getSkuCode()).isEqualTo("SKU-Ax2+SKU-B");
        assertThat(dto.getSku()).isEqualTo("SKU-A,SKU-B");
    }

    @Test
    void includesWarehouseAndChannelInManagerDownloadHeaders() {
        assertThat(OrderDownloadDto.headerAlias())
                .containsEntry("deliveryChannel", "渠道")
                .containsEntry("storehouse", "仓库");
        assertThat(OrderDownloadDto.auditHeaderAlias())
                .containsEntry("deliveryChannel", "渠道")
                .containsEntry("storehouse", "仓库");

        assertThat(OrderDownloadDto.filterAudit(column("deliveryChannel"), false))
                .isTrue();
        assertThat(OrderDownloadDto.filterAudit(column("storehouse"), false))
                .isTrue();
    }

    private Order order() {
        return Order.builder()
                .contextInfo(OrderContextInfo.builder()
                        .currencyFractionDigits(2)
                        .country("台湾")
                        .countryCode("TW")
                        .build())
                .deliveryInfo(OrderDeliveryInfo.builder()
                        .firstName("测试")
                        .lastName("")
                        .phone("123456")
                        .province("台湾省/Taiwan")
                        .city("台北市/Taipei")
                        .district("中正區/Zhongzheng")
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

    private OrderTemplateColumn column(String fieldKey) {
        return OrderTemplateColumn.builder()
                .fieldKey(fieldKey)
                .headerName(fieldKey)
                .build();
    }
}
