package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
import cn.v7soft.admin.service.ICurrencyService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrencyService  extends BaseService<Currency, CurrencyRepository> implements ICurrencyService {
    public CurrencyService(CurrencyRepository repository) {
        super(repository);
    }

    @Override
    protected void checkKeyConstraint(Currency data) {
        Currency currency = repository.findBySameName(data.getName(), data.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(currency, "货币名称不允许重复");
    }

    @Override
    public CurrencyResponse recommendByLanguage(Long languageId) {
        Optional<Currency> recommend = repository.getRecommendByLanguage(languageId);
        return recommend.map(CurrencyResponse::convertEntity).orElseGet(() -> CurrencyResponse.builder().build());
    }

    @Override
    public Optional<Currency> getByCode(String currencyCode) {
        if (StrUtil.isBlank(currencyCode)) {
            return Optional.empty();
        }
        return repository.findByCode(currencyCode.trim());
    }
}
