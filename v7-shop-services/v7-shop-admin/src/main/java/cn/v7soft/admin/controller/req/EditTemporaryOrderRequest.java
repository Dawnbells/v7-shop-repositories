package cn.v7soft.admin.controller.req;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.NumberUtil;
import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrderContextInfo;
import cn.v7soft.dao.entities.primary.TemporaryOrderItemInfo;
import cn.v7soft.dao.entities.primary.TemporaryOrderRiskRecordInfo;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "订单请求实体类")
public class EditTemporaryOrderRequest extends IdRequest {
    @Schema(description = "公司ID")
    private Long companyId;
    @Schema(description = "订单来源")
    private String from;

    @Schema(description = "来源网址")
    private String fromUrl;

    @Schema(description = "平台")
    private WebsiteTypeEnum platform;

    @Schema(description = "原始订单ID")
    private String originOrderId;

    @Schema(description = "订单时间")
    private LocalDateTime orderTime;

    @Schema(description = "配送信息")
    private TemporaryOrderDeliveryInfoRequest deliveryInfo;

    @Schema(description = "财务信息")
    private TemporaryOrderFinancialInfoRequest financialInfo;

    @Schema(description = "支付信息")
    private TemporaryOrderPaymentInfoRequest paymentInfo;

    @Schema(description = "商品信息列表")
    private List<TemporaryOrderItemInfoRequest> itemInfos;

    @Schema(description = "上下文信息")
    private TemporaryOrderContextInfoRequest contextInfo;

    @Schema(description = "风险记录信息")
    private TemporaryOrderRiskRecordInfoRequest riskInfo;

    public OrderPaymentInfo toPaymentInfo() {
        OrderPaymentInfo orderPaymentInfo = new OrderPaymentInfo();
        BeanUtil.copyProperties(this.paymentInfo, orderPaymentInfo);
        return orderPaymentInfo;
    }

    public OrderFinancialInfo toFinancialInfo() {
        OrderFinancialInfo orderFinancialInfo = new OrderFinancialInfo();
        BeanUtil.copyProperties(this.financialInfo, orderFinancialInfo);
        return orderFinancialInfo;
    }

    public OrderDeliveryInfo toDeliveryInfo() {
        OrderDeliveryInfo orderDeliveryInfo = new OrderDeliveryInfo();
        BeanUtil.copyProperties(this.deliveryInfo, orderDeliveryInfo);
        return orderDeliveryInfo;
    }

    public TemporaryOrderContextInfo toContextInfo(SystemUser owner) {
        TemporaryOrderContextInfo orderContextInfo = new TemporaryOrderContextInfo();
        BeanUtil.copyProperties(this.contextInfo, orderContextInfo);
        if (owner.getId() != 1L) {
            orderContextInfo.setSalesUid(owner.getId());
            orderContextInfo.setSalesPerson(owner.getName());
            if (owner.getDepartment()!=null) {
                orderContextInfo.setDepartment(owner.getDepartment().getName());
                orderContextInfo.setDepartmentId(owner.getDepartment().getId());
            } else {
                orderContextInfo.setDepartment("");
                orderContextInfo.setDepartmentId(null);
            }
        }
        return orderContextInfo;
    }

    public TemporaryOrderRiskRecordInfo toRiskInfo() {
        TemporaryOrderRiskRecordInfo orderRiskRecordInfo = new TemporaryOrderRiskRecordInfo();
        BeanUtil.copyProperties(this.riskInfo, orderRiskRecordInfo);
        this.riskInfo.setRealIp(null);
        this.riskInfo.setDeviceId(null);
        return orderRiskRecordInfo;
    }

    public List<TemporaryOrderItemInfo> toItemInfos() {
        if (this.itemInfos == null || this.itemInfos.isEmpty()) {
            return List.of();
        }
        CopyOptions options = CopyOptions.create().setIgnoreProperties("spuId", "productId", "image");
        List<TemporaryOrderItemInfo> result = new java.util.ArrayList<>(this.itemInfos.size());
        for (TemporaryOrderItemInfoRequest req : this.itemInfos) {
            TemporaryOrderItemInfo item = new TemporaryOrderItemInfo();
            BeanUtil.copyProperties(req, item, options);
            item.setSpuId(parseLong(req.getSpuId()));
            item.setProductId(parseLong(req.getProductId()));
            if (item.getMerchandise() == null) {
                item.setMerchandise("");
            }
            result.add(item);
        }
        return result;
    }

    private static long parseLong(String value) {
        return NumberUtil.isNumber(value) ? Long.parseLong(value) : 0L;
    }
}
