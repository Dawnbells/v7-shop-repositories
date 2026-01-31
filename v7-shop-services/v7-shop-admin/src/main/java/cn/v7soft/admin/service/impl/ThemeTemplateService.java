package cn.v7soft.admin.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.IThemeTemplateService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.AndQueryAttribute;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.OrQueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ThemeTemplate;
import cn.v7soft.dao.enums.ShareType;
import cn.v7soft.dao.repositories.primary.ThemeTemplateRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ThemeTemplateService extends BaseDataRangeService<ThemeTemplate, ThemeTemplateRepository> implements IThemeTemplateService {

    public ThemeTemplateService(ThemeTemplateRepository repository) {
        super(repository);
    }

    @Override
    protected void addIgnoreAccessDataRageCondition(OrQueryAttribute<ThemeTemplate> or) {
        // 公司级共享模板 - 公司全员可见
        or.add(EqualsQueryAttribute.builder().name("shareType").value(ShareType.COMPANY).build());
        
        // 部门级共享模板 - 同部门可见
        or.add(
                AndQueryAttribute
                        .create(null)
                        .add(new AccessDataRangeAttribute(AccessDataRangeLevel.DEPARTMENT))
                        .add(EqualsQueryAttribute.builder().name("shareType").value(ShareType.DEPARTMENT).build())
        );
    }

    @Override
    protected void checkKeyConstraint(ThemeTemplate data) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        ThemeTemplate existing = repository.findBySameName(data.getName(), data.getId(), user.getLongId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existing, "模板名称不允许重复");
    }

    @Override
    @Transactional
    public ThemeTemplate copyFromTemplate(Long sourceId, String name) {
        ThemeTemplate source = findById(sourceId)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("源模板不存在"));

        ThemeTemplate newTemplate = ThemeTemplate.builder()
                .name(name)
                .description(source.getDescription())
                .coverImage(source.getCoverImage())
                .themeConfig(source.getThemeConfig())
                .variableSchema(source.getVariableSchema())
                .siteConfig(source.getSiteConfig())
                .variableValues(source.getVariableValues())
                .shareType(ShareType.PRIVATE)
                .sharedFrom(source)
                .build();

        return save(newTemplate);
    }

    @Override
    public List<ThemeTemplate> remoteQuery(String keyword) {
        QueryPageRequest<ThemeTemplate> request = QueryPageRequest.fromRequest(new BasePageRequest());
        request.addConstraint(StrUtil.isNotBlank(keyword), LikeAttribute.builder()
                .name("name")
                .value(keyword)
                .leftMatch(true)
                .rightMatch(true)
                .build());
        request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        return findPaginated(request).getContent();
    }

    @Override
    @Transactional
    public void updateThemeConfig(Long id, String themeConfig, String variableSchema, String siteConfig, String variableValues) {
        ThemeTemplate template = findById(id)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("模板不存在"));

        SystemUserDto user = SaSessionUtil.getLoginUser();
        if (template.getOwner() != null && !template.getOwner().getId().equals(user.getLongId())) {
            if (!SaSessionUtil.getLoginUser().isAdmin()) {
                throw ClientResponseEnum.PERMISSION_DENIED.newException("只有模板所有者可以编辑");
            }
        }

        template.setThemeConfig(themeConfig);
        template.setVariableSchema(variableSchema);
        template.setSiteConfig(siteConfig);
        template.setVariableValues(variableValues);

        saveAndFlush(template);
    }
}
