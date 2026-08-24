package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 渠道/仓库三态语义：null=列不存在不动；空白=清除（置 null）；非空=trim 后覆盖。
 */
class OrderCheckInfoDtoTest {

    @Test
    void keepsChannelAndStorehouseWhenColumnsAbsent() {
        Order order = orderWithLogistics("云途", "深圳仓");
        new OrderCheckInfoDto().fillChangeOrder(order);
        assertThat(order.getLogisticsInfo().getDeliveryChannel()).isEqualTo("云途");
        assertThat(order.getLogisticsInfo().getStorehouse()).isEqualTo("深圳仓");
    }

    @Test
    void clearsChannelAndStorehouseWhenBlank() {
        Order order = orderWithLogistics("云途", "深圳仓");
        OrderCheckInfoDto dto = new OrderCheckInfoDto();
        dto.setDeliveryChannel("");
        dto.setStorehouse(" \t ");
        dto.fillChangeOrder(order);
        assertThat(order.getLogisticsInfo().getDeliveryChannel()).isNull();
        assertThat(order.getLogisticsInfo().getStorehouse()).isNull();
    }

    @Test
    void clearsEachColumnIndependently() {
        Order order = orderWithLogistics("云途", "深圳仓");
        OrderCheckInfoDto dto = new OrderCheckInfoDto();
        dto.setDeliveryChannel("");
        dto.fillChangeOrder(order);
        assertThat(order.getLogisticsInfo().getDeliveryChannel()).isNull();
        assertThat(order.getLogisticsInfo().getStorehouse()).isEqualTo("深圳仓");
    }

    @Test
    void trimsNonBlankValuesBeforeSaving() {
        Order order = orderWithLogistics(null, null);
        OrderCheckInfoDto dto = new OrderCheckInfoDto();
        dto.setDeliveryChannel(" 云途 ");
        dto.setStorehouse("\t美西仓 ");
        dto.fillChangeOrder(order);
        assertThat(order.getLogisticsInfo().getDeliveryChannel()).isEqualTo("云途");
        assertThat(order.getLogisticsInfo().getStorehouse()).isEqualTo("美西仓");
    }

    @Test
    void skipsWhenLogisticsInfoAbsentAndValuesBlank() {
        Order order = new Order();
        OrderCheckInfoDto dto = new OrderCheckInfoDto();
        dto.setDeliveryChannel("");
        dto.setStorehouse("  ");
        dto.fillChangeOrder(order);
        assertThat(order.getLogisticsInfo()).isNull();
    }

    @Test
    void createsLogisticsInfoForNonBlankValues() {
        Order order = new Order();
        OrderCheckInfoDto dto = new OrderCheckInfoDto();
        dto.setDeliveryChannel("云途");
        dto.fillChangeOrder(order);
        assertThat(order.getLogisticsInfo()).isNotNull();
        assertThat(order.getLogisticsInfo().getDeliveryChannel()).isEqualTo("云途");
        assertThat(order.getLogisticsInfo().getStorehouse()).isNull();
    }

    @Test
    void updatesOrderQuantityWithoutChangingItemQuantity() {
        OrderItemInfo item = OrderItemInfo.builder().quantity(1L).build();
        Order order = new Order();
        order.setQuantity(1L);
        order.setItemInfos(List.of(item));
        OrderCheckInfoDto dto = new OrderCheckInfoDto();
        dto.setQuantity(" 3 ");

        dto.fillChangeOrder(order);

        assertThat(order.getQuantity()).isEqualTo(3L);
        assertThat(item.getQuantity()).isEqualTo(1L);
    }

    @Test
    void keepsOrderQuantityWhenUploadValueIsMissingOrBlank() {
        Order order = new Order();
        order.setQuantity(4L);

        new OrderCheckInfoDto().fillChangeOrder(order);
        assertThat(order.getQuantity()).isEqualTo(4L);

        OrderCheckInfoDto blankQuantity = new OrderCheckInfoDto();
        blankQuantity.setQuantity(" 	 ");
        blankQuantity.fillChangeOrder(order);
        assertThat(order.getQuantity()).isEqualTo(4L);
    }

    @Test
    void rejectsInvalidQuantityBeforeChangingOrder() {
        for (String invalidQuantity : List.of("0", "-1", "1.5", "abc")) {
            Order order = new Order();
            order.setQuantity(4L);
            order.setSkuCodes("OLD-SKU");
            OrderCheckInfoDto dto = new OrderCheckInfoDto();
            dto.setQuantity(invalidQuantity);
            dto.setSkuCodes("NEW-SKU");

            assertThatThrownBy(() -> dto.fillChangeOrder(order))
                    .hasMessageContaining("数量必须为正整数");
            assertThat(order.getQuantity()).isEqualTo(4L);
            assertThat(order.getSkuCodes()).isEqualTo("OLD-SKU");
        }
    }

    private Order orderWithLogistics(String channel, String storehouse) {
        Order order = new Order();
        order.setLogisticsInfo(OrderLogisticsInfo.builder()
                .deliveryChannel(channel)
                .storehouse(storehouse)
                .build());
        return order;
    }
}
