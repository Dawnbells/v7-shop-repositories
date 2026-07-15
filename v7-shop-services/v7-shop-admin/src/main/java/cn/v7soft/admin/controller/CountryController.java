package cn.v7soft.admin.controller;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.EditCountryRequest;
import cn.v7soft.admin.controller.req.QueryCountryRequest;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.common.controller.resp.CountryResponse;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.meta.CountryMeta;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.enums.AddressOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/country")
@Tag(name = "建站系统/国家管理")
public class CountryController extends BaseController<Country, ICountryService, CountryResponse, QueryCountryRequest, EditCountryRequest> {

    protected CountryController(ICountryService service) {
        super(service);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<CountryResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<Country> request = QueryPageRequest.fromRequest(QueryCountryRequest.builder().pageSize(50).pageNo(1).build());
        //noinspection DuplicatedCode
        if (StringUtils.hasText(query)) {
            request.or()
                    .addConstraint(ConvertUtils.isLong(query), (country) -> EqualsQueryAttribute.builder().name("id").value(Long.valueOf(query.trim())).build())
                    .add(LikeAttribute.builder().name("name").value(query.trim()).build())
                    .add(LikeAttribute.builder().name("code").value(query.trim()).build())
                    .next();
        }
        return service.findPaginated(request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build()))
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @Override
    protected CountryResponse convertEntity(Country country) {
        return CountryResponse.convertEntity(country);
    }

    @Override
    protected Country convertRequest(@Nullable Country dbEntity, EditCountryRequest request) {
        Country country = Optional.ofNullable(dbEntity).orElse(Country.builder().build());
        BeanUtil.copyProperties(request, country);
        country.setContinentCode(request.getContinentCode());
        country.setCode(country.getCode().toUpperCase());
        // 构建 CountryMeta，包含所有元数据字段
        CountryMeta countryMeta = CountryMeta.builder()
                .phonePrefix(request.getPhonePrefix())
                .phoneRule(request.getPhoneRule())
                .addressRule(request.getAddressRule())
                .addressFields(request.getAddressFields())
                .addressOrder(AddressOrder.defaultIfNull(request.getAddressOrder()))
                .useFullName(request.getUseFullName())
                .footerCopyrightInfo(request.getFooterCopyrightInfo())
                .requiredPhone(request.getRequiredPhone() != null ? request.getRequiredPhone() : false)
                .requiredEmail(request.getRequiredEmail() != null ? request.getRequiredEmail() : false)
                .build();
        country.setCountryMeta(countryMeta);
        country.setLanguages(request.getLanguageIds().stream().map((Function<String, Language>) s -> Language.builder().id(Long.parseLong(s)).build()).collect(Collectors.toList()));
        country.setCurrency(Currency.builder().id(Long.parseLong(request.getCurrencyId())).build());
        country.setFrontServer(FrontServer.builder().id(Long.parseLong(request.getFrontServerId())).build());
        log.debug("edit country: " + JSONUtil.toJsonStr(country));
        return country;
    }

    @Override
    protected String getPermissionPrefix() {
        return "country";
    }
}
