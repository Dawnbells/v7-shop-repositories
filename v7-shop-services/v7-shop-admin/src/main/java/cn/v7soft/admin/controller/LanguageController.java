package cn.v7soft.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.common.controller.resp.LanguageResponse;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.admin.controller.req.EditLanguageRequest;
import cn.v7soft.admin.controller.req.QueryLanguageRequest;
import cn.v7soft.admin.service.ILanguageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Validated
@RestController
@RequestMapping("/language")
@Tag(name = "建站系统/语言管理")
public class LanguageController extends BaseController<Language, ILanguageService, LanguageResponse, QueryLanguageRequest, EditLanguageRequest> {
    protected LanguageController(ILanguageService service) {
        super(service);
    }


    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<LanguageResponse> remoteQuery(@RequestParam("query") String query) {
        log.debug("query = " + query);
        QueryPageRequest<Language> request = QueryPageRequest.fromRequest(QueryLanguageRequest.builder().pageSize(Integer.MAX_VALUE).pageNo(1).build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(LikeAttribute.builder().name("name").value("%" + query.trim() + "%").build())
                    .add(LikeAttribute.builder().name("cname").value("%" + query.trim() + "%").build())
                    .next()
                    .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }

        return service.findPaginated(request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build()))
                .stream().map(this::convertEntityCopyId).collect(Collectors.toList());
    }

    @Override
    protected LanguageResponse convertEntity(Language language) {
        return LanguageResponse.convertEntity(language);
    }

    @Override
    protected Language convertRequest(@Nullable Language dbEntity, EditLanguageRequest request) {
        Language language = Optional.ofNullable(dbEntity).orElse(Language.builder().build());
        BeanUtil.copyProperties(request, language);
        language.setCode(language.getCode().toUpperCase());
        return language;
    }

    @Override
    protected String getPermissionPrefix() {
        return "language";
    }
}
