package cn.v7soft.admin.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.PostThemeConfigRequest;
import cn.v7soft.admin.service.ISubDomainService;
import cn.v7soft.admin.service.IThemeCustomService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ThemeCustom;
import cn.v7soft.dao.repositories.primary.ThemeCustomRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.transaction.Transactional;

@Service
public class ThemeCustomService extends BaseDataRangeService<ThemeCustom, ThemeCustomRepository> implements IThemeCustomService {

    private ISubDomainService subDomainService;

    public ThemeCustomService(ThemeCustomRepository repository) {
        super(repository);
    }

    @Lazy
    @Autowired
    public void setSubDomainService(ISubDomainService subDomainService) {
        this.subDomainService = subDomainService;
    }

    @Override
    protected void checkKeyConstraint(ThemeCustom data) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        ThemeCustom existing = repository.findBySameName(data.getName(), data.getId(), user.getLongId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existing, "主题名称不允许重复");
    }

    @Override
    @Transactional
    public void postConfig(PostThemeConfigRequest request) {
        ThemeCustom themeCustom = findById(request.getIdLongValue()).orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("该主题不存在"));
        themeCustom.setThemeConfig(JSONUtil.parseObj(request.getTheme()));
        themeCustom.setI18nConfig(JSONUtil.parseObj(request.getI18n()));
        themeCustom.setBaseConfig(JSONUtil.parseObj(request.getBase()));
        themeCustom.setTemplateConfig(JSONUtil.parseObj(request.getTemplate()));
        saveAndFlush(themeCustom);
    }

    @Override
    public boolean clearDomainTheme(DeleteRequest request) {
        List<Long> idList = request.getIdList();
        if (idList.isEmpty()) {
            return true;
        }
        subDomainService.clearDomainThemes(idList);
        return true;
    }
}

