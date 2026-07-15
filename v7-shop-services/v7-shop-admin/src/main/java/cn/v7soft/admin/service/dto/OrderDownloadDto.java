package cn.v7soft.admin.service.dto;

import java.math.RoundingMode;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.util.TextUtils;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderBotCheckInfo;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import cn.v7soft.dao.entities.primary.OrderRiskRecordInfo;
import cn.v7soft.dao.entities.primary.OrderTemplateColumn;
import cn.v7soft.dao.enums.AddressOrder;
import cn.v7soft.dao.enums.PaymentMethod;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class OrderDownloadDto {

    /**
     * ID
     */
    private String id;
    /**
     * 是否COD
     */
    private String cod;
    /**
     * 总价
     */
    private String totalAmount;
    /**
     * 订单编号
     */
    private String orderNo;
    /**
     * 原单号
     */
    private String originOrderId;
    /**
     * 商品ID
     */
    private String productId;
    /**
     * 品名1
     */
    private String skuName;
    /**
     * sku代码
     */
    private String skuCode;
    /**
     * 件数
     */
    private String quantity;
    /**
     * 赠品名称
     */
    private String freebiesName;
    /**
     * 赠品SKU
     */
    private String freebiesSkuCode;
    /**
     * 物流单号
     */
    private String trackingNumber;
    /**
     * 订单状态
     */
    private String orderStatus;
    /**
     * 审单状态
     */
    private String checkOrderStatus;
    /**
     * 审单备注
     */
    private String checkOrderRemark;
    /**
     * 审单提示
     */
    private String checkOrderReminder;
    /**
     * 订单来源平台
     */
    private String fromPlatform;
    /**
     * SKU
     */
    private String sku;
    /**
     * 中文名称
     */
    private String merchandise;
    /**
     * 面单品名
     */
    private String waybillProductName;
    /**
     * 出货渠道
     */
    private String deliveryChannel;
    /**
     * 产品名称
     */
    private String productName;
    /**
     * 订单重复数
     */
    private int orderDuplicationCount;
    /**
     * ip重复数
     */
    private int ipDuplicationCount;
    /**
     * IP地址
     */
    private String remoteIp;
    /**
     * 用户邮箱
     */
    private String email;
    /**
     * 重量
     */
    private String weight;
    /**
     * 尺寸
     */
    private String dimensions;
    /**
     * 产品套餐
     */
    private String specTitle;
    /**
     * spec item Quantity
     */
    private String itemQuantity;
    /**
     * spec item price
     */
    private String itemPrice;
    /**
     * 姓
     */
    private String name;
    /**
     * 手机号
     */
    private String phone;
    /**
     * 省
     */
    private String province;
    /**
     * 市
     */
    private String city;
    /**
     * 区
     */
    private String district;
    /**
     * 邮编
     */
    private String postalCode;
    /**
     * 是否偏远地区
     */
    private String isRemoteArea;
    /**
     * 地址
     */
    private String address;
    /**
     * 详细地址,包含省市区
     */
    private String fullAddress;
    /**
     * 是否已配送
     */
    private String isDelivered;
    /**
     * 邮编1
     */
    private String postalCode1;
    /**
     * 邮编1
     */
    private String postalCode2;
    /**
     * 物流1
     */
    private String logistics1;
    /**
     * 物流2
     */
    private String logistics2;
    /**
     * 物流编号
     */
    private String logisticsNumber;
    /**
     * 备注
     */
    private String remark;
    /**
     * 下单时间
     */
    private String createTime;
    /**
     * 产品归属人
     */
    private String sellerName;
    /**
     * 币种
     */
    private String currencyName;
    /**
     * 币种代码
     */
    private String currencyCode;
    /**
     * 产品归属部门
     */
    private String department;

    /**
     * 国家代码
     */
    private String countryCode;

    /**
     * 国家
     */
    private String country;
    /**
     * 导单日期
     */
    private String importDate;
    /**
     * 导单人员
     */
    private String importEmployee;
    /**
     * 转寄单号
     */
    private String forwardingTrackingNumber;
    /**
     * 退件单号
     */
    private String returnTrackingNumber;
    /**
     * 普/特货
     */
    private String cargoType;
    /**
     * 仓库
     */
    private String storehouse;
    /**
     * 物流名称
     */
    private String logisticsName;

    /**
     * 订单域名
     */
    private String domain;

    static String buildFullAddress(AddressOrder addressOrder, String province, String city,
                                   String district, String address) {
        String normalizedProvince = normalizeRegion(province);
        String normalizedCity = normalizeRegion(city);
        String normalizedDistrict = normalizeRegion(district);
        Stream<String> parts = addressOrder == AddressOrder.FORWARD
                ? Stream.of(normalizedProvince, normalizedCity, normalizedDistrict, address)
                : Stream.of(address, normalizedDistrict, normalizedCity, normalizedProvince);
        return parts.filter(StrUtil::isNotBlank).collect(Collectors.joining(" "));
    }

    private static String normalizeRegion(String region) {
        if (StrUtil.isBlank(region)) {
            return region;
        }
        return region.split("/")[0];
    }

    public static OrderDownloadDto convert(Order order) {
        return convert(order, AddressOrder.REVERSE);
    }

    public static OrderDownloadDto convert(Order order, AddressOrder addressOrder) {
        String originOrderId = order.getOriginOrderId();
        int currencyFractionDigits = order.getContextInfo().getCurrencyFractionDigits();
        OrderDeliveryInfo deliveryInfo = order.getDeliveryInfo();
        OrderLogisticsInfo logisticsInfo = order.getLogisticsInfo();
        OrderContextInfo contextInfo = order.getContextInfo();
        OrderRiskRecordInfo riskInfo = order.getRiskInfo();
        OrderBotCheckInfo botOrderCheckInfo = order.getBotOrderCheckInfo();
        OrderFinancialInfo financialInfo = order.getFinancialInfo();
        List<OrderItemInfo> orderItemInfos = order.getItemInfos();
        OrderItemInfo orderItemInfo = orderItemInfos.isEmpty() ? null : orderItemInfos.get(0);
        String orderNoAlias = order.getOrderNoAlias();
        String orderNo = StrUtil.isBlank(orderNoAlias) ? String.valueOf(order.getId()) : orderNoAlias;
        Long quantity = orderItemInfos.stream().map(OrderItemInfo::getQuantity).reduce(0L, Long::sum);

        String province = normalizeRegion(deliveryInfo.getProvince());
        String city = normalizeRegion(deliveryInfo.getCity());
        String district = normalizeRegion(deliveryInfo.getDistrict());
        String fullAddress = buildFullAddress(addressOrder, province, city, district, deliveryInfo.getAddress());
        String importTime = LocalDateTimeUtil.format(order.getImportTime(), "yyyy-MM-dd");
        if (StrUtil.equalsIgnoreCase("1970-01-01", importTime)) {
            importTime = "";
        }


        if (logisticsInfo == null) {
            logisticsInfo = OrderLogisticsInfo.builder().build();
        }

        if (botOrderCheckInfo == null) {
            botOrderCheckInfo = OrderBotCheckInfo.builder().build();
        }

        if (riskInfo == null) {
            riskInfo = OrderRiskRecordInfo.builder().build();
        }
        String domain = "";
        try {
            domain = URLUtil.getHost(new URL(order.getFromUrl())).getHost();
        } catch (Throwable e) {
            domain = "";
        }

        String merchandiseLeft = null;
        if(orderItemInfo != null && orderItemInfo.getMerchandise() != null && orderItemInfo.getMerchandise().contains("=") && order.getPlatform() == WebsiteTypeEnum.V7_SHOP) {
            merchandiseLeft = orderItemInfo.getMerchandise().split("=")[0] + "=";
        }

        return OrderDownloadDto.builder()
                .id(String.valueOf(order.getId()))
                .cod(order.getPaymentInfo().getPaymentMethod() == PaymentMethod.COD ? "是" : "否")
                .totalAmount(financialInfo.getTotalAmount().setScale(currencyFractionDigits, RoundingMode.HALF_UP).toPlainString())
                .orderNo(orderNo)
                .originOrderId(originOrderId)
                .productId(orderItemInfo == null ? "" : String.valueOf(orderItemInfo.getId()))
                .skuName((merchandiseLeft == null ? "" : merchandiseLeft) + (orderItemInfo == null ? "" : orderItemInfo.getSkuName()))
                .skuCode(orderItemInfo == null ? "" : orderItemInfo.getSkuCode())
                .quantity(String.valueOf(quantity))
                .freebiesName("")
                .freebiesSkuCode("")
                .trackingNumber(logisticsInfo.getTrackingNumber())
                .orderStatus(order.getOrderStatus().getName())
                .checkOrderStatus(order.getBotOrderStatus().getName())
                .checkOrderRemark(order.getOrderCheckRemark())
                .checkOrderReminder(botOrderCheckInfo.toTip())
                .fromPlatform(order.getFrom())
                .sku(orderItemInfos.isEmpty()? "":orderItemInfos.stream().map(OrderItemInfo::getSkuCode).collect(Collectors.joining(",")))
                .merchandise(orderItemInfo == null ? "" : orderItemInfo.getMerchandise())
                .waybillProductName(logisticsInfo.getWaybillProductName())
                .deliveryChannel(logisticsInfo.getDeliveryChannel())
                .productName(orderItemInfo == null ? "" : orderItemInfo.getTitle())
                .orderDuplicationCount(botOrderCheckInfo.getPhoneRepeatCount())
                .ipDuplicationCount(botOrderCheckInfo.getRemoteIpRepeatCount())
                .remoteIp(riskInfo.getRemoteIp())
                .email(deliveryInfo.getEmail())
                .weight("")
                .dimensions("")
                .specTitle(orderItemInfo == null ? "" : orderItemInfo.getSpecTitle())
                .itemQuantity(orderItemInfo == null ? "0" : orderItemInfo.getQuantity().toString())
                .itemPrice(orderItemInfo == null ? "0" : orderItemInfo.getSellPrice().setScale(currencyFractionDigits, RoundingMode.HALF_UP).toPlainString())
                .name(deliveryInfo.getFirstName() + (TextUtils.isBlank(deliveryInfo.getLastName()) ? "" : (" " + deliveryInfo.getLastName())))
                .phone(deliveryInfo.getPhone() == null ? "" : deliveryInfo.getPhone().replaceAll(" ", ""))
                .country(contextInfo.getCountry())
                .province(province)
                .city(city)
                .district(district)
                .isRemoteArea(deliveryInfo.isRemoteArea() ? "是" : "否")
                .address(deliveryInfo.getAddress())
                .fullAddress(fullAddress)
                .isDelivered(logisticsInfo.isDelivered() ? "是" : "否")
                .postalCode(deliveryInfo.getPostalCode())
                .postalCode1(logisticsInfo.getPostal1())
                .postalCode2(logisticsInfo.getPostal2())
                .logistics1(logisticsInfo.getLogistics1())
                .logistics2(logisticsInfo.getLogistics2())
                .logisticsNumber(logisticsInfo.getTrackingNumber())
                .remark(deliveryInfo.getRemark())
                .createTime(order.getOrderTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .sellerName(contextInfo.getSalesPerson())
                .currencyName(contextInfo.getCurrencyName())
                .currencyCode(contextInfo.getCurrencyCode())
                .department(contextInfo.getDepartment())
                .countryCode(contextInfo.getCountryCode())
                .importDate(importTime)
                .forwardingTrackingNumber(logisticsInfo.getForwardingTrackingNumber())
                .storehouse(logisticsInfo.getStorehouse())
                .logisticsName(logisticsInfo.getName())
                .domain(domain)
                .importEmployee("")
                .cargoType("")
                .returnTrackingNumber("")
                .build();
    }

    public static LinkedHashMap<String, String> headerAlias() {
        LinkedHashMap<String, String> headerAliasMap = new LinkedHashMap<>();
        headerAliasMap.put("orderNo", "订单编号");
        headerAliasMap.put("originOrderId", "原单号");
        headerAliasMap.put("sellerName", "姓名");
        headerAliasMap.put("merchandise", "中文品名");
        headerAliasMap.put("skuName", "品名1");
        headerAliasMap.put("skuCode", "sku1");
        headerAliasMap.put("quantity", "订单数量");
        headerAliasMap.put("domain", "域名");
        headerAliasMap.put("totalAmount", "价格");
        headerAliasMap.put("country", "国家");
        headerAliasMap.put("fromPlatform", "订单来源");
        headerAliasMap.put("deliveryChannel", "渠道");
        headerAliasMap.put("storehouse", "仓库");
        headerAliasMap.put("createTime", "出单时间");
        return headerAliasMap;
    }

    public static LinkedHashMap<String, String> auditHeaderAlias() {
        LinkedHashMap<String, String> auditHeaderAliasMap = new LinkedHashMap<>();
        auditHeaderAliasMap.put("cod", "是否COD");
        auditHeaderAliasMap.put("itemPrice", "单价");
        auditHeaderAliasMap.put("orderNo", "订单编号");
        auditHeaderAliasMap.put("originOrderId", "原单号");
        auditHeaderAliasMap.put("skuName", "品名1");
        auditHeaderAliasMap.put("skuCode", "sku1");
        auditHeaderAliasMap.put("merchandise", "中文名称");
        auditHeaderAliasMap.put("waybillProductName", "面单品名");
        auditHeaderAliasMap.put("deliveryChannel", "渠道");
        auditHeaderAliasMap.put("productName", "产品名称");
        auditHeaderAliasMap.put("email", "邮箱");
        auditHeaderAliasMap.put("specTitle", "产品套餐");
        auditHeaderAliasMap.put("quantity", "件数");
        auditHeaderAliasMap.put("totalAmount", "价格");
        auditHeaderAliasMap.put("name", "客户姓名");
        auditHeaderAliasMap.put("phone", "客户手机");
        auditHeaderAliasMap.put("country", "国家");
        auditHeaderAliasMap.put("countryCode", "国家代码");
        auditHeaderAliasMap.put("province", "省份/州");
        auditHeaderAliasMap.put("city", "城市");
        auditHeaderAliasMap.put("district", "地区");
        auditHeaderAliasMap.put("address", "地址");
        auditHeaderAliasMap.put("fullAddress", "详细地址");
        auditHeaderAliasMap.put("postalCode", "邮编");
        auditHeaderAliasMap.put("isRemoteArea", "偏远地区");
        auditHeaderAliasMap.put("remark", "备注");
        auditHeaderAliasMap.put("createTime", "下单时间");
        auditHeaderAliasMap.put("sellerName", "产品归属人");
        auditHeaderAliasMap.put("currencyCode", "币种");
        auditHeaderAliasMap.put("department", "部门");
        auditHeaderAliasMap.put("importDate", "导单日期");
        auditHeaderAliasMap.put("storehouse", "仓库");
        auditHeaderAliasMap.put("remoteIp", "IP地址");
        auditHeaderAliasMap.put("ipDuplicationCount", "IP数");
        auditHeaderAliasMap.put("orderDuplicationCount", "订单数");
        auditHeaderAliasMap.put("orderStatus", "订单状态");
        auditHeaderAliasMap.put("checkOrderStatus", "审单状态");
        auditHeaderAliasMap.put("checkOrderRemark", "审单备注");
        auditHeaderAliasMap.put("checkOrderReminder", "审单提示");
        auditHeaderAliasMap.put("fromPlatform", "订单来源");
        return auditHeaderAliasMap;
    }

    public static boolean filterAudit(OrderTemplateColumn column, Boolean isAudit) {
        if (Boolean.TRUE.equals(isAudit)) {
            return true;
        }
        return headerAlias().containsKey(column.getFieldKey());
    }
}
