package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.OrderItemInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 用于返回订单商品信息的响应类
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "订单商品信息响应")
public class OrderItemInfoResponse {

    @Schema(title = "商品标题", example = "iPhone 13 Pro")
    private String title;

    @Schema(title = "主图")
    private MultimediaFileResponse image;

    @Schema(title = "商品售价", example = "799.99")
    private BigDecimal sellPrice;

    @Schema(title = "原价", example = "899.99")
    private BigDecimal originPrice;

    @Schema(title = "成本价", example = "500.00")
    private BigDecimal costPrice;

    @Schema(title = "税费", example = "20.00")
    private BigDecimal tax;

    @Schema(title = "条码", example = "1234567890123")
    private String barcode;

    @Schema(title = "购买数量", example = "2")
    private Long quantity;

    @Schema(title = "SKU ID", example = "1")
    private Long skuId;

    @Schema(title = "SKU 编码", example = "SKU12345")
    private String skuCode;

    @Schema(title = "SKU 名称", example = "iPhone 13 Pro 256GB")
    private String skuName;

    @Schema(title = "是否为虚拟 SKU", example = "false")
    private boolean skuIsVirtual;

    @Schema(title = "中文品名", example = "false")
    private String merchandise;

    @Schema(title = "面单品名", example = "Suport mobil pentru motociclete")
    private String waybillProductName;

    /**
     * 从 `OrderItemInfo` 实体转换为 `OrderItemInfoResponse` 的静态方法
     *
     * @param item 订单商品实体
     * @return 订单商品响应对象
     */
    public static OrderItemInfoResponse convertEntity(OrderItemInfo item) {
        return OrderItemInfoResponse.builder()
                .title(item.getTitle())
                .image(MultimediaFileResponse.convertEntity(item.getImage()))
                .sellPrice(item.getSellPrice())
                .originPrice(item.getOriginPrice())
                .costPrice(item.getCostPrice())
                .tax(item.getTax())
                .barcode(item.getBarcode())
                .quantity(item.getQuantity())
                .skuId(item.getSkuId())
                .skuCode(item.getSkuCode())
                .skuName(item.getSkuName())
                .skuIsVirtual(item.isSkuIsVirtual())
                .merchandise(item.getMerchandise())
                .waybillProductName(item.getWaybillProductName())
                .build();
    }
}
