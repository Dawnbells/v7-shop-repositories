package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.meta.CountryMeta;
import cn.v7soft.dao.enums.AddressOrder;
import cn.v7soft.dao.repositories.primary.CountryRepository;
import cn.v7soft.admin.service.ICountryService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Override
    public Map<String, AddressOrder> getAddressOrders(Collection<String> countryCodes) {
        Set<String> normalizedCodes = Optional.ofNullable(countryCodes)
                .stream()
                .flatMap(Collection::stream)
                .filter(StrUtil::isNotBlank)
                .map(code -> code.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, AddressOrder> addressOrders = new LinkedHashMap<>();
        normalizedCodes.forEach(code -> addressOrders.put(code, AddressOrder.REVERSE));
        if (normalizedCodes.isEmpty()) {
            return addressOrders;
        }
        repository.findAllByCodeIn(normalizedCodes).forEach(country -> {
            if (StrUtil.isBlank(country.getCode())) {
                return;
            }
            CountryMeta meta = country.getCountryMeta();
            AddressOrder addressOrder = meta == null ? null : meta.getAddressOrder();
            addressOrders.put(country.getCode().trim().toUpperCase(),
                    AddressOrder.defaultIfNull(addressOrder));
        });
        return addressOrders;
    }

}
