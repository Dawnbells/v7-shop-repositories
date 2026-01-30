package cn.v7soft.dao.entities.primary;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Entity
@Table(name = "t_order_logistics_infos")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OrderLogisticsInfo extends BaseEntity {
    /**
     * 是否已配送
     */
    @Column(name = "is_delivered")
    private boolean delivered;
    /**
     * 物流单号
     */
    @Column(name = "tracking_number")
    private String trackingNumber;
    /**
     * 转寄单号
     */
    @Column(name = "forwarding_tracking_number")
    private String forwardingTrackingNumber;
    /**
     * 名单品名
     */
    @Column(name = "waybill_product_name")
    private String waybillProductName;

    /**
     * 出货渠道
     */
    @Column(name = "delivery_channel")
    private String deliveryChannel;
    /**
     * 仓库
     */
    private String storehouse;
    /**
     * 物流名称
     */
    private String name;
    /**
     * 邮编1
     */
    private String postal1;
    /**
     * 邮编2
     */
    private String postal2;
    /**
     * 物流1
     */
    private String logistics1;
    /**
     * 物流2
     */
    private String logistics2;
}
