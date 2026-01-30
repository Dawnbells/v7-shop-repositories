package cn.v7soft.admin.service;

import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.Currency;

import java.util.Optional;

public interface ICurrencyService extends IBaseService<Currency> {
    /**
     * 根据语言推荐关联的币种
     * @param languageId 语言ID
     * @return 币种
     */
    CurrencyResponse recommendByLanguage(Long languageId);

    Optional<Currency> getByCode(String currencyCode);

}
