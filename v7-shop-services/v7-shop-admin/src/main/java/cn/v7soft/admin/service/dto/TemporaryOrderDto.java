package cn.v7soft.admin.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.utils.V7IdentifierGenerator;
import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderBotCheckInfo;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.enums.CheckStatus;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TemporaryOrderDto extends IdDto {

    private Long companyId;
    /**
     * 实体类状态
     */
    private StatusEnum status;
    /**
     * 实体类创建时间
     */
    private LocalDateTime createTime;
    /**
     * 实体类更新时间
     */
    private LocalDateTime updateTime;
    /**
     * 归属用户
     */
    private String ownerId;
    private String ownerName;
    /**
     * 订单来源
     */
    private String from;
    /**
     * 订单来源URL
     */
    private String fromUrl;
    /**
     * 订单平台
     */
    private WebsiteTypeEnum platform;

    /**
     * 原始订单号
     */
    private String originOrderId;
    /**
     * 下单时间
     */
    private LocalDateTime orderTime;
    /**
     * 订单收货信息
     */
    private OrderDeliveryInfoDto deliveryInfo;
    /**
     * 订单金额相关信息
     */
    private OrderFinancialInfo financialInfo;
    /**
     * 支付相关信息
     */
    private OrderPaymentInfo paymentInfo;
    /**
     * 商品信息
     */
    private List<TemporaryOrderItemInfoDto> itemInfos;
    /**
     * 订单归属信息
     */
    private TemporaryOrderContextInfoDto contextInfo;
    /**
     * 访问风险记录
     */
    private TemporaryRiskRecordInfoDto riskInfo;

    public static TemporaryOrderDto convert(TemporaryOrder temporaryOrder) {
        TemporaryOrderContextInfoDto contextInfoDto = TemporaryOrderContextInfoDto.convert(temporaryOrder.getContextInfo());
        SystemUser owner = temporaryOrder.getOwner();
        return TemporaryOrderDto.builder()
                .id(String.valueOf(temporaryOrder.getId()))
                .status(temporaryOrder.getStatus())
                .createTime(temporaryOrder.getCreateTime())
                .updateTime(temporaryOrder.getUpdateTime())
                .companyId(temporaryOrder.getCompanyId())
                .ownerId(owner != null ? String.valueOf(owner.getId()): "")
                .ownerName(owner != null ? owner.getName(): "")
                .from((temporaryOrder.getFrom()))
                .fromUrl(temporaryOrder.getFromUrl())
                .platform(temporaryOrder.getPlatform())
                .originOrderId(temporaryOrder.getOriginOrderId())
                .orderTime(temporaryOrder.getOrderTime())
                .deliveryInfo(OrderDeliveryInfoDto.convert(temporaryOrder.getDeliveryInfo(), contextInfoDto.getPhonePrefix()))
                .financialInfo(temporaryOrder.getFinancialInfo())
                .paymentInfo(temporaryOrder.getPaymentInfo())
                .itemInfos(temporaryOrder.getItemInfos().stream().map(TemporaryOrderItemInfoDto::convert).toList())
                .contextInfo(TemporaryOrderContextInfoDto.convert(temporaryOrder.getContextInfo()))
                .riskInfo(TemporaryRiskRecordInfoDto.convert(temporaryOrder.getRiskInfo()))
                .build();
    }

    public Order toOrderInfo() {
        String waybillProductName = CollectionUtil.isEmpty(itemInfos) ? "" : itemInfos.get(0).getWaybillProductName();
        return Order.builder()
                .id(getLongId())
                .orderNoAlias("")
                .status(status)
                .createTime(createTime)
                .updateTime(updateTime)
                .companyId(companyId)
                .owner(SystemUser.builder().id(Long.valueOf(ownerId)).build())
                .from(from)
                .fromUrl(fromUrl)
                .platform(platform)
                .originOrderId(originOrderId)
                .orderTime(orderTime)
                .itemCount(itemInfos.size())
                .orderStatus(OrderStatus.PENDING)
                .orderCheckRemark("")
                .botOrderStatus(CheckStatus.PENDING)
                .importTime(LocalDateTimeUtil.of(0))
                .paymentInfo(paymentInfo)
                .financialInfo(financialInfo)
                .deliveryInfo(deliveryInfo.toDeliveryInfo())
                .itemInfos(itemInfos.stream().map(TemporaryOrderItemInfoDto::toOrderItemInfo).toList())
                .contextInfo(contextInfo.toOrderContextInfo())
                .riskInfo(riskInfo.toOrderRiskInfo())
                .botOrderCheckInfo(new OrderBotCheckInfo())
                .logisticsInfo(
                        OrderLogisticsInfo.builder()
                                .waybillProductName(waybillProductName)
                                .build()
                )
                .build();
    }
}
