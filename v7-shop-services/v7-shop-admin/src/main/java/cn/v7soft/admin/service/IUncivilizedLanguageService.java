package cn.v7soft.admin.service;

import cn.v7soft.dao.entities.primary.UncivilizedLanguage;

import java.util.List;

public interface IUncivilizedLanguageService {

    List<UncivilizedLanguage> listByLanguageId(long languageId);
}
