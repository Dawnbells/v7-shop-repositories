package cn.v7soft.common.controller.resp;

import java.util.List;
import java.util.stream.Collectors;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.meta.CountryMeta;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.enums.AddressOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 用于返回国家信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "国家信息响应")
public class CountryResponse extends IdResponse {

    @Schema(title = "国家名称", example = "中国")
    private String name;

    @Schema(title = "国家代码", example = "CN")
    private String code;

    @Schema(title = "归属大陆", example = "EU")
    private String continentCode;

    @Schema(title = "货币信息")
    private CurrencyResponse currency;

    @Schema(title = "语言信息")
    private List<LanguageResponse> languages;

    @Schema(title = "使用的商城服务器")
    private FrontServerResponse frontServer;

    // ========== CountryMeta 字段 ==========

    @Schema(title = "电话前缀", example = "+86")
    private String phonePrefix;

    @Schema(title = "电话验证规则", example = "^1[3-9]\\d{9}$")
    private String phoneRule;

    @Schema(title = "地址验证规则", example = "^.{5,200}$")
    private String addressRule;

    @Schema(title = "地址包含字段", example = "province,city,district,postal_code")
    private String addressFields;

    @Schema(title = "地址拼接顺序", example = "REVERSE")
    private AddressOrder addressOrder;

    @Schema(title = "是否使用全名", example = "true")
    private Boolean useFullName;

    @Schema(title = "底部版权信息", example = "© 2024 V7Shop. All rights reserved.")
    private String footerCopyrightInfo;

    @Schema(title = "是否必填电话", example = "true")
    private Boolean requiredPhone;

    @Schema(title = "是否必填邮箱", example = "false")
    private Boolean requiredEmail;

    public static CountryResponse convertEntity(Country country) {
        if (country == null) {
            return null;
        }
        CountryResponseBuilder<?, ?> builder = CountryResponse.builder()
                .id(String.valueOf(country.getId()))
                .name(country.getName())
                .code(country.getCode())
                .continentCode(country.getContinentCode())
                .addressOrder(AddressOrder.REVERSE);
        if (country.getCurrency() != null) {
            builder.currency(CurrencyResponse.convertEntity(country.getCurrency()));
        }
        if (country.getLanguages() != null) {
            builder.languages(country.getLanguages().stream().map(LanguageResponse::convertEntity).collect(Collectors.toList()));
        }
        if (country.getFrontServer() != null) {
            builder.frontServer(FrontServerResponse.convertEntity(country.getFrontServer()));
        }
        // 填充 CountryMeta 字段
        CountryMeta meta = country.getCountryMeta();
        if (meta != null) {
            builder.phonePrefix(meta.getPhonePrefix())
                    .phoneRule(meta.getPhoneRule())
                    .addressRule(meta.getAddressRule())
                    .addressFields(meta.getAddressFields())
                    .addressOrder(AddressOrder.defaultIfNull(meta.getAddressOrder()))
                    .useFullName(meta.getUseFullName())
                    .footerCopyrightInfo(meta.getFooterCopyrightInfo())
                    .requiredPhone(meta.getRequiredPhone())
                    .requiredEmail(meta.getRequiredEmail());
        }
        return filling(country, builder.build());
    }
}
