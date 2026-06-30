package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.entities.primary.OrderTemplateColumn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDownloadDtoTest {

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

    private OrderTemplateColumn column(String fieldKey) {
        return OrderTemplateColumn.builder()
                .fieldKey(fieldKey)
                .headerName(fieldKey)
                .build();
    }
}
