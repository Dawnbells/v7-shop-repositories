package cn.v7soft.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.admin.controller.req.EditCurrencyRequest;
import cn.v7soft.admin.controller.req.QueryCurrencyRequest;
import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.admin.service.ICurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/currency")
@Tag(name = "建站系统/货币管理")
public class CurrencyController extends BaseController<Currency, ICurrencyService, CurrencyResponse, QueryCurrencyRequest, EditCurrencyRequest> {
    protected CurrencyController(ICurrencyService service) {
        super(service);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<CurrencyResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<Currency> request = QueryPageRequest.fromRequest(QueryCurrencyRequest.builder().pageNo(1).build());
        //noinspection DuplicatedCode
        if (StringUtils.hasText(query)) {
            request.add(
                    LikeAttribute.builder()
                            .name("name")
                            .value("%" + query.trim() + "%")
                            .build()
            ).add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }
        return service.findPaginated(request)
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @Override
    protected CurrencyResponse convertEntity(Currency currency) {
        return CurrencyResponse.convertEntity(currency);
    }

    @Override
    protected Currency convertRequest(@Nullable Currency dbEntity, EditCurrencyRequest request) {
        Currency currency = Optional.ofNullable(dbEntity).orElse(Currency.builder().build());
        BeanUtil.copyProperties(request, currency);
        currency.setCode(currency.getCode().toUpperCase());
        return currency;
    }

    /**
     * 根据语言推荐币种
     * @return 币种
     */
    @Operation(summary = "根据语言推荐币种")
    @PostMapping("/recommendByLang/{languageId}")
    public CurrencyResponse recommendByLanguage(@PathVariable("languageId") Long languageId) {
        return service.recommendByLanguage(languageId);
    }

    @Override
    protected String getPermissionPrefix() {
        return "currency";
    }
}
