package cn.v7soft.dao.enums;

/**
 * 第三方商城订单同步时使用的币种模式。
 * SHOP_MONEY: 使用店铺结算币种（Shopline shop_money）
 * PRESENTMENT_MONEY: 使用订单展示币种（Shopline presentment_money，即客户下单时看到的币种）
 */
public enum CurrencyMode {
    SHOP_MONEY,
    PRESENTMENT_MONEY
}
