package cn.v7soft.dao.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SearchType {
    ORDER_ID("订单编号"),
    MERCHANDISE("中文品名"),
    TELEPHONE("手机号码"),
    NAME("客户姓名"),
    PRODUCT_TITLE("产品标题"),
    REMOTE_IP("远程IP"),
    ADDRESS("客户地址"),
    DOMAIN("下单域名"),
    REPEAT("重单查询");

    private final String label;
}
