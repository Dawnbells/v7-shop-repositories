package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.repositories.primary.LanguageRepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.admin.service.ILanguageService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LanguageService extends BaseService<Language, LanguageRepository> implements ILanguageService {
    private final SpuRepository spuRepository;
    public LanguageService(LanguageRepository repository, SpuRepository spuRepository) {
        super(repository);
        this.spuRepository = spuRepository;
    }

    @Override
    protected void checkKeyConstraint(Language data) {
        Language language = repository.findBySameName(data.getName(), data.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(language, "语言名称不允许重复");
    }

    @Override
    public Optional<Language> getByCode(String languageCode) {
        if (StrUtil.isBlank(languageCode)) {
            return Optional.empty();
        }
        return repository.getByCode(languageCode.trim().toUpperCase());
    }
}
