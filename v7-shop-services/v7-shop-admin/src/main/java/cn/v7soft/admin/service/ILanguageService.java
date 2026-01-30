package cn.v7soft.admin.service;

import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.Language;

import java.util.Optional;

public interface ILanguageService extends IBaseService<Language> {
    Optional<Language> getByCode(String language);
}
