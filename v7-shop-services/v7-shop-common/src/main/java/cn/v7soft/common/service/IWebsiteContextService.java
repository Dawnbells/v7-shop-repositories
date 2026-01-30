package cn.v7soft.common.service;

import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.common.controller.resp.WebsiteResponse;

public interface IWebsiteContextService {
    /**
     * 根据当前商城ID获取商城名称
     *
     * @return 商城名称
     */
    String getCurrentWebsiteName();


    /**
     * 根据当前商城ID获取商城货币
     * @return 商城货币
     */
    CurrencyResponse getCurrentWebsiteCurrency();

    WebsiteResponse getCurrentWebsite();
}
