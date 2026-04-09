package cn.v7soft.admin.controller.resp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.util.TextUtils;
import com.fasterxml.jackson.annotation.JsonFormat;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.enums.CheckStatus;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "订单信息响应")
public class OrderResponse extends IdResponse {

    @Schema(title = "订单来源", example = "Website")
    private String from;

    @Schema(title = "订单来源URL", example = "https://example.com")
    private String fromUrl;

    @Schema(title = "sku名称")
    private String skuNames;

    @Schema(title = "sku代码")
    private String skuCodes;

    @Schema(title = "下单数量")
    private long quantity;

    @Schema(title = "原始订单号")
    private String originOrderId;

    @Schema(title = "下单平台")
    private WebsiteTypeEnum platform;

    @Schema(title = "支付信息")
    private OrderPaymentInfo paymentInfo;

    @Schema(title = "财务信息")
    private OrderFinancialInfo financialInfo;

    @Schema(title = "配送信息")
    private OrderDeliveryInfoResponse deliveryInfo;

    @Schema(title = "上下文信息")
    private OrderContextInfoResponse contextInfo;

    @Schema(title = "机器审单提示")
    private BotOrderCheckInfoResponse botOrderCheckInfo;

    @Schema(title = "风险信息")
    private RiskRecordInfoResponse riskRecordInfo;

    @Schema(title = "产品信息")
    private List<OrderItemInfoResponse> items;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @Schema(title = "机审状态")
    private CheckStatus botOrderStatus;

    @Schema(title = "人审状态")
    private OrderStatus orderStatus;
    @Schema(title = "审单批注")
    private String orderCheckRemark;

    @Schema(title = "是否已建联")
    private Boolean contacted;

    /**
     * 从 `Order` 实体转换为 `OrderResponse` 的静态方法。
     */
    public static OrderResponse convertEntity(TemporaryOrder order, boolean desensitized) {
        return OrderResponse.builder().build();
    }

    public static OrderResponse convertEntity(Order order, boolean desensitized) {
        String fromUrl = order.getFromUrl();
        if (TextUtils.isBlank(order.getFromUrl()) && !order.getItemInfos().isEmpty()) {
            fromUrl = "https://" + order.getContextInfo().getWebsiteUrl() + "/product/" + order.getItemInfos().get(0).getSpuId();
        }
        if (order.getPlatform() == WebsiteTypeEnum.V7_SHOP) {
            fromUrl = fromUrl.split("\\?")[0];
        }
        return OrderResponse.builder()
                .id(String.valueOf(order.getId()))
                .originOrderId(order.getOriginOrderId())
                .from(order.getFrom())
                .platform(order.getPlatform())
                .fromUrl(fromUrl)
                .paymentInfo(order.getPaymentInfo())
                .financialInfo(order.getFinancialInfo())
                .deliveryInfo(OrderDeliveryInfoResponse.convert(order.getDeliveryInfo(), desensitized))
                .botOrderCheckInfo(BotOrderCheckInfoResponse.convert(order.getBotOrderCheckInfo()))
                .contextInfo(OrderContextInfoResponse.convert(order.getContextInfo()))
                .createTime(order.getOrderTime())
                .riskRecordInfo(RiskRecordInfoResponse.convert(order.getRiskInfo()))
                .items(order.getItemInfos().stream().map(OrderItemInfoResponse::convertEntity).collect(Collectors.toList()))
                .botOrderStatus(order.getBotOrderStatus())
                .orderStatus(order.getOrderStatus())
                .orderCheckRemark(order.getOrderCheckRemark())
                .skuCodes(order.getSkuCodes())
                .skuNames(order.getSkuNames())
                .quantity(order.getQuantity())
                .contacted(order.getContacted())
                .build();
    }
}
