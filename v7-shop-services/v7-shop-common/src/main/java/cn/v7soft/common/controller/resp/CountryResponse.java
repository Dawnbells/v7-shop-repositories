package cn.v7soft.common.controller.resp;

import java.util.List;
import java.util.stream.Collectors;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Country;
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

    public static CountryResponse convertEntity(Country country) {
        if (country == null) {
            return null;
        }
        CountryResponseBuilder<?, ?> builder = CountryResponse.builder()
                .id(String.valueOf(country.getId()))
                .name(country.getName())
                .code(country.getCode())
                .continentCode(country.getContinentCode());
        if (country.getCurrency() != null) {
            builder.currency(CurrencyResponse.convertEntity(country.getCurrency()));
        }
        if (country.getLanguages() != null) {
            builder.languages(country.getLanguages().stream().map(LanguageResponse::convertEntity).collect(Collectors.toList()));
        }
        if (country.getFrontServer() != null) {
            builder.frontServer(FrontServerResponse.convertEntity(country.getFrontServer()));
        }
        return filling(country, builder.build());
    }
}
