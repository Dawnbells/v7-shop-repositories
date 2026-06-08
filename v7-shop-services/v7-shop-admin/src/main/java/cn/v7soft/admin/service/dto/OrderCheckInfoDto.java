package cn.v7soft.admin.service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.v7soft.admin.utils.DateTimeHelper;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import cn.v7soft.dao.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCheckInfoDto {

    /**
     * 订单编号
     */
    private String orderId;
    /**
     * SKU 代码，多个逗号分隔
     */
    private String skuCodes;
    /**
     * SKU 名称，多个逗号分隔
     */
    private String skuNames;

    /**
     * 中文名称
     */
    private String chineseName;
    /**
     * 面单品名
     */
    private String waybillItemName;
    /**
     * 产品名称
     */
    private String productName;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 产品套餐
     */
    private String productPackage;
    /**
     * 数量
     */
    private String quantity;
    /**
     * 价格
     */
    private String price;
    /**
     * 客户姓名
     */
    private String customerName;
    /**
     * 客户手机
     */
    private String customerPhone;
    /**
     * 省份/州
     */
    private String provinceOrState;
    /**
     * 城市
     */
    private String city;
    /**
     * 地区
     */
    private String district;
    /**
     * 详细地址
     */
    private String detailedAddress;
    /**
     * 邮编
     */
    private String postalCode;
    /**
     * 备注
     */
    private String remarks;
    /**
     * 下单时间
     */
    private String orderTime;
    /**
     * 产品归属人
     */
    private String productOwner;
    /**
     * 部门
     */
    private String department;
    /**
     * 导单日期
     */
    private String orderDate;
    /**
     * 订单状态
     */
    private String orderStatus;
    /**
     * 审单备注
     */
    private String orderCheckRemark;
    /**
     * 出货渠道（渠道）
     */
    private String deliveryChannel;
    /**
     * 仓库
     */
    private String storehouse;
    public static final Map<String, String> KEY_MAPPING = new HashMap<>() {
        {
            put("orderId", "订单编号");
            put("skuCodes", "SKU1");
            put("skuNames", "SKU品名");
            put("chineseName", "中文名称");
            put("waybillItemName", "面单品名");
            put("productName", "产品名称");
            put("email", "邮箱");
            put("productPackage", "产品套餐");
            put("quantity", "QUANTITY");
            put("price", "价格");
            put("customerName", "客户姓名");
            put("customerPhone", "客户手机");
            put("provinceOrState", "省份/州");
            put("city", "城市");
            put("district", "地区");
            put("detailedAddress", "详细地址");
            put("postalCode", "邮编");
            put("remarks", "备注");
            put("orderTime", "下单时间");
            put("productOwner", "产品归属人");
            put("department", "部门");
            put("orderDate", "导单日期");
            put("orderStatus", "审单状态");
            put("logist", "物流状态");
            put("orderCheckRemark", "审单批注");
            put("deliveryChannel", "渠道");
            put("storehouse", "仓库");
        }
    };

    public void fillChangeOrder(Order order) {
        if (order.getItemInfos() != null && !order.getItemInfos().isEmpty()) {
            OrderItemInfo orderItemInfo = order.getItemInfos().get(0);
            if (chineseName != null) {
                orderItemInfo.setMerchandise(chineseName);
            }
            if (waybillItemName != null) {
                orderItemInfo.setWaybillProductName(waybillItemName);
            }
            if (productName != null) {
                orderItemInfo.setTitle(productName);
            }
            if (productPackage != null) {
                orderItemInfo.setSpecTitle(productPackage);
            }
        }
        if (order.getDeliveryInfo() != null) {
            OrderDeliveryInfo deliveryInfo = order.getDeliveryInfo();
            if (email != null) {
                deliveryInfo.setEmail(email);
            }
            if (customerName != null) {
                deliveryInfo.setFirstName(customerName);
            }
            if (customerPhone != null) {
                deliveryInfo.setPhone(customerPhone);
            }
            if (provinceOrState != null) {
                deliveryInfo.setProvince(provinceOrState);
            }
            if (city != null) {
                deliveryInfo.setCity(city);
            }
            if (district != null) {
                deliveryInfo.setDistrict(district);
            }
            if (detailedAddress != null) {
                deliveryInfo.setAddress(detailedAddress);
            }
            if (postalCode != null) {
                deliveryInfo.setPostalCode(postalCode);
            }
            if (remarks != null) {
                deliveryInfo.setRemark(remarks);
            }
        }

        if (order.getFinancialInfo() != null) {
            OrderFinancialInfo financialInfo = order.getFinancialInfo();
            if (price != null) {
                financialInfo.setTotalAmount(new BigDecimal(price));
            }
        }
        if(order.getContextInfo() != null) {
            OrderContextInfo contextInfo = order.getContextInfo();
            if (productOwner != null) {
                contextInfo.setSalesPerson(productOwner);
            }
            if (department != null) {
                contextInfo.setDepartment(department);
            }
        }
        if (deliveryChannel != null || storehouse != null) {
            OrderLogisticsInfo logisticsInfo = order.getLogisticsInfo();
            if (logisticsInfo == null) {
                logisticsInfo = OrderLogisticsInfo.builder().build();
                order.setLogisticsInfo(logisticsInfo);
            }
            if (deliveryChannel != null) {
                logisticsInfo.setDeliveryChannel(deliveryChannel);
            }
            if (storehouse != null) {
                logisticsInfo.setStorehouse(storehouse);
            }
        }
        if (orderTime != null) {
            order.setOrderTime(DateTimeHelper.parseLocalDateTime(orderTime));
        }
        if (orderDate != null) {
            order.setImportTime(Objects.requireNonNull(DateTimeHelper.parseLocalDate(orderDate)).atStartOfDay());
        }
        if (orderStatus != null) {
            order.setOrderStatus(OrderStatus.fromName(orderStatus));
        }
        if (orderCheckRemark != null) {
            order.setOrderCheckRemark(orderCheckRemark);
        }
        if (skuCodes != null) {
            order.setSkuCodes(skuCodes);
        }
        if (skuNames != null) {
            order.setSkuNames(skuNames);
        }
        if (quantity != null && ConvertUtils.isLong(quantity)) {
            order.setQuantity(ConvertUtils.parseLong(quantity));
        }
        if (order.getImportTime() == null || LocalDateTimeUtil.of(0).equals(order.getImportTime())) {
            order.setImportTime(LocalDateTime.now());
        }
    }
}
