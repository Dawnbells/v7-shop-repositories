package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IUncivilizedLanguageService;
import cn.v7soft.dao.entities.primary.UncivilizedLanguage;
import cn.v7soft.dao.repositories.primary.UncivilizedLanguageRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UncivilizedLanguageService implements IUncivilizedLanguageService {
    private final static String UNCIVILIZED_LANGUAGE_CACHE_NAME = "uncivilized_language";
    private final UncivilizedLanguageRepository repository;


    @Override
    @Cacheable(value = UNCIVILIZED_LANGUAGE_CACHE_NAME, key = "#languageId", cacheManager = "cacheManager", unless = "#result == null")
    public List<UncivilizedLanguage> listByLanguageId(long languageId) {
        return repository.findAllByLanguage(languageId);
    }
}
