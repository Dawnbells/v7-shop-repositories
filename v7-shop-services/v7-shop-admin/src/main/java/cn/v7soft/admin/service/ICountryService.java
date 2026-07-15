package cn.v7soft.admin.service;

import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.enums.AddressOrder;
import java.util.Collection;
import java.util.Map;

import java.util.Optional;

public interface ICountryService extends IBaseService<Country> {
    Optional<Country> getByCode(String countryCode);

    Map<String, AddressOrder> getAddressOrders(Collection<String> countryCodes);
}
