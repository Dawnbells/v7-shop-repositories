package cn.v7soft.admin.service;

import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.Country;

import java.util.Optional;

public interface ICountryService extends IBaseService<Country> {
    Optional<Country> getByCode(String countryCode);
}
