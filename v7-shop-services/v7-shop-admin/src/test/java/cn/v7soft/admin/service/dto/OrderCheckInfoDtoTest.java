package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    private Order orderWithLogistics(String channel, String storehouse) {
        Order order = new Order();
        order.setLogisticsInfo(OrderLogisticsInfo.builder()
                .deliveryChannel(channel)
                .storehouse(storehouse)
                .build());
        return order;
    }
}
