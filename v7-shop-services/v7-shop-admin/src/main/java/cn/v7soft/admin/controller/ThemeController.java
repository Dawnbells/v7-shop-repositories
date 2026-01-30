package cn.v7soft.admin.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditThemeCustomRequest;
import cn.v7soft.admin.controller.req.PostThemeConfigRequest;
import cn.v7soft.admin.controller.req.QueryThemeCustomRequest;
import cn.v7soft.admin.controller.resp.ThemeCustomResponse;
import cn.v7soft.admin.dao.ThemeConfig;
import cn.v7soft.admin.service.IThemeCustomService;
import cn.v7soft.admin.utils.ThemeLoader;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ThemeCustom;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/themes")
@Tag(name = "建站系统/主题管理")
public class ThemeController extends BaseDataRangeController<ThemeCustom, IThemeCustomService, ThemeCustomResponse, QueryThemeCustomRequest, EditThemeCustomRequest> {

    protected ThemeController(IThemeCustomService service) {
        super(service);
    }

    @Override
    protected QueryPageRequest<ThemeCustom> convertQueryPageRequest(QueryThemeCustomRequest request) {
        return super.convertQueryPageRequest(request)
                .addConstraint(StrUtil.isNotBlank(request.getName()), LikeAttribute.builder().name("name").value(request.getName()).leftMatch(true).build())
                .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
    }

    @Override
    protected ThemeCustomResponse convertEntity(ThemeCustom entity) {
        return ThemeCustomResponse.convertEntity(entity);
    }

    @Override
    protected ThemeCustom convertRequest(@Nullable ThemeCustom dbEntity, EditThemeCustomRequest request) {
        ThemeCustom theme = dbEntity == null ? ThemeCustom.builder().build() : dbEntity;
        BeanUtil.copyProperties(request, theme);
        UnmodifiableList<ThemeConfig> themes = ThemeLoader.getThemes();
        ThemeConfig themeConfig = themes.stream().filter((config) -> StrUtil.equals(config.getName(), request.getTemplateName())).findFirst().orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("不支持改主题模板：" + request.getTemplateName()));
        theme.setBaseConfig(themeConfig.getBaseValues());
        theme.setTemplateConfig(themeConfig.getTemplateValues());
        theme.setI18nConfig(themeConfig.getI18nValues());
        theme.setThemeConfig(themeConfig.getThemeValues());
        return theme;
    }

    @Override
    protected String getPermissionPrefix() {
        return "theme";
    }

    @GetMapping("/templates")
    @Operation(summary = "获取模板列表")
    public List<ThemeConfig> templateList() {
        return ThemeLoader.getThemes();
    }

    @PostMapping("/config")
    @Operation(summary = "设置自定义主题配置")
    public void postConfig(@Valid @RequestBody PostThemeConfigRequest request) {
        service.postConfig(request);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<ThemeCustomResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<ThemeCustom> request = QueryPageRequest.fromRequest(QueryThemeCustomRequest.builder().build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(LikeAttribute.builder().name("name").value("%" + query.trim() + "%").build())
                    .add(LikeAttribute.builder().name("description").value("%" + query.trim() + "%").build())
                    .next();
        }
        request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        return service.findPaginated(request)
                .stream().map(this::convertEntityCopyId).collect(Collectors.toList());
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return service.clearDomainTheme(request);
    }
}
