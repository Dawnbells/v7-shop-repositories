package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.repositories.primary.CountryRepository;
import cn.v7soft.admin.service.ICountryService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CountryService extends BaseService<Country, CountryRepository> implements ICountryService {
    public CountryService(CountryRepository repository) {
        super(repository);
    }

    @Override
    protected void checkKeyConstraint(Country data) {
        Country existingCountry = repository.findByName(data.getName(), data.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existingCountry, "国家名称不允许重复");
    }

    @Override
    public Optional<Country> getByCode(String countryCode) {
        if (StrUtil.isBlank(countryCode)) {
            return Optional.empty();
        }
        return repository.getByCode(countryCode.trim().toUpperCase());
    }

}
