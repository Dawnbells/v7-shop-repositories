package cn.v7soft.common.controller.resp;

import cn.v7soft.dao.entities.primary.Website;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用于返回网站信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "网站信息响应")
public class  WebsiteResponse extends DataRangeResponse {
    @Schema(title = "网站名称", example = "我的商城")
    private String name;

    @Schema(title = "国家信息")
    private CountryResponse country;

    @Schema(title = "语言信息")
    private List<LanguageResponse> languages;

    @Schema(title = "货币信息")
    private CurrencyResponse currency;

    /**
     * 从 `Website` 实体转换为 `WebsiteResponse` 的静态方法。
     */
    public static WebsiteResponse convertEntity(Website website) {
        if (website == null) {
            return null;
        }
        return DataRangeResponse.filling(website, WebsiteResponse.builder()
                .name(website.getName())
                .country(CountryResponse.convertEntity(website.getCountry()))
                .languages(website.getLanguages().stream().map(LanguageResponse::convertEntity).collect(Collectors.toList()))
                .currency(CurrencyResponse.convertEntity(website.getCurrency()))
                .build());
    }
}
