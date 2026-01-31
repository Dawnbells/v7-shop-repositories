package cn.v7soft.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.*;
import cn.v7soft.admin.controller.resp.ThemeTemplateResponse;
import cn.v7soft.admin.service.IThemeTemplateService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ThemeTemplate;
import cn.v7soft.dao.enums.ShareType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/theme-templates")
@Tag(name = "建站系统/主题模板管理")
public class ThemeTemplateController extends BaseDataRangeController<ThemeTemplate, IThemeTemplateService, ThemeTemplateResponse, QueryThemeTemplateRequest, EditThemeTemplateRequest> {

    public ThemeTemplateController(IThemeTemplateService service) {
        super(service);
    }

    @Override
    protected QueryPageRequest<ThemeTemplate> convertQueryPageRequest(QueryThemeTemplateRequest request) {
        QueryPageRequest<ThemeTemplate> pageRequest = super.convertQueryPageRequest(request);
        pageRequest.addConstraint(StrUtil.isNotBlank(request.getName()),
                LikeAttribute.builder().name("name").value(request.getName()).leftMatch(true).rightMatch(true).build());
        pageRequest.addConstraint(request.getShareType() != null,
                                  EqualsQueryAttribute.builder().name("shareType").value(request.getShareType()).build());
        pageRequest.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        return pageRequest;
    }

    @Override
    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<ThemeTemplateResponse> page(@Valid @RequestBody QueryThemeTemplateRequest request) {
        return service.findPaginated(convertQueryPageRequest(request))
                .map(this::convertEntityCopyId);
    }

    @Override
    protected ThemeTemplateResponse convertEntity(ThemeTemplate entity) {
        return ThemeTemplateResponse.convertEntity(entity);
    }

    @Override
    protected ThemeTemplate convertRequest(@Nullable ThemeTemplate dbEntity, EditThemeTemplateRequest request) {
        ThemeTemplate template;
        
        // 如果是从现有模板复制
        if (request.getCopyFromId() != null && dbEntity == null) {
            template = service.copyFromTemplate(request.getCopyFromId(), request.getName());
            // 复制后再更新其他属性
            if (StrUtil.isNotBlank(request.getDescription())) {
                template.setDescription(request.getDescription());
            }
            if (StrUtil.isNotBlank(request.getCoverImage())) {
                template.setCoverImage(request.getCoverImage());
            }
            if (request.getShareType() != null) {
                template.setShareType(request.getShareType());
            }
            return template;
        }
        
        // 新建或编辑
        template = dbEntity == null ? ThemeTemplate.builder().build() : dbEntity;
        BeanUtil.copyProperties(request, template, "copyFromId");
        
        // 默认为私有
        if (template.getShareType() == null) {
            template.setShareType(ShareType.PRIVATE);
        }
        
        return template;
    }

    @Override
    protected String getPermissionPrefix() {
        return "themeTemplate";
    }

    @PostMapping("/copy")
    @Operation(summary = "从现有模板复制创建新模板")
    public ThemeTemplateResponse copyFromTemplate(@Valid @RequestBody CopyThemeTemplateRequest request) {
        ThemeTemplate newTemplate = service.copyFromTemplate(request.getSourceId(), request.getName());
        return convertEntityCopyId(newTemplate);
    }

    @PostMapping("/updateConfig")
    @Operation(summary = "更新模板的主题配置")
    public void updateConfig(@Valid @RequestBody UpdateThemeTemplateConfigRequest request) {
        service.updateThemeConfig(
                request.getId(),
                request.getThemeConfig(),
                request.getVariableSchema(),
                request.getSiteConfig(),
                request.getVariableValues()
        );
    }

    @GetMapping("/remoteQuery")
    @Operation(summary = "远程搜索模板")
    public List<ThemeTemplateResponse> remoteQuery(@RequestParam(value = "query", required = false) String query) {
        return service.remoteQuery(query)
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取模板详情")
    public ThemeTemplateResponse getById(@PathVariable("id") Long id) {
        return service.findById(id)
                .map(this::convertEntityCopyId)
                .orElse(null);
    }
}
