package cn.v7soft.admin.service.dto;

import java.math.BigDecimal;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.entities.primary.TemporaryOrderItemInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TemporaryOrderItemInfoDto extends IdDto {
    /**
     * SPU ID
     */
    private Long spuId;
    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 商品标题
     */
    private String title;
    /**
     * 商品标题
     */
    private String specTitle;
    /**
     * 主图
     */
    private MultimediaFile image;

    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 商品售价
     */
    private BigDecimal sellPrice;

    /**
     * 原价
     */
    private BigDecimal originPrice;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 税费
     */
    private BigDecimal tax;

    /**
     * 条码
     */
    private String barcode;

    /**
     * 购买数量
     */
    private Long quantity;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * SKU 编码
     */
    private String skuCode;

    /**
     * 品名
     */
    private String skuName;

    /**
     * 是否是虚拟 SKU
     */
    private boolean skuIsVirtual;

    /**
     * 中文品名
     */
    private String merchandise;
    /**
     * 面单品名
     */
    private String waybillProductName;
    public static TemporaryOrderItemInfoDto convert(TemporaryOrderItemInfo itemInfo) {
        TemporaryOrderItemInfoDto itemInfoDto = TemporaryOrderItemInfoDto.builder().build();
        BeanUtil.copyProperties(itemInfo, itemInfoDto);
        return itemInfoDto;
    }

    public OrderItemInfo toOrderItemInfo() {
        OrderItemInfo orderItemInfo = new OrderItemInfo();
        BeanUtil.copyProperties(this, orderItemInfo);
        return orderItemInfo;
    }
}


