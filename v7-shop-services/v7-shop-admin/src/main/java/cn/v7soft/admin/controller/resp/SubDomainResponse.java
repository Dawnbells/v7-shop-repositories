package cn.v7soft.admin.controller.resp;

import java.util.List;

import cn.v7soft.common.controller.resp.CountryResponse;
import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.common.controller.resp.LanguageResponse;
import cn.v7soft.common.controller.resp.WebsiteResponse;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 用于返回二级域名信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "二级域名信息响应")
public class SubDomainResponse extends DataRangeResponse {

    @Schema(title = "二级域名名称", example = "subdomain")
    private String name;

    @Schema(title = "二级域名全称", example = "subdomain.example.com")
    private String fullName;

    @Schema(title = "跳转域名", example = "null")
    private SubDomainResponse redirectDomain;

    @Schema(title = "一级域名信息")
    private TopLevelDomainResponse parentDomain;

    @Schema(title = "网站信息")
    private WebsiteResponse website;

    @Schema(title = "皮肤信息")
    private ThemeCustomResponse theme;

    @Schema(title = "已绑定像素列表")
    private List<PixelAccountResponse> pixels;

    @Schema(title = "绑定的国家信息")
    private CountryResponse country;

    @Schema(title = "缓存的货币信息")
    private CurrencyResponse currency;

    @Schema(title = "缓存的语言信息")
    private LanguageResponse language;

    /**
     * 从 `SubDomain` 实体转换为 `SubDomainResponse` 的静态方法。
     */
    public static SubDomainResponse convertEntity(SubDomain subDomain) {
        if (subDomain == null) {
            return null;
        }
        List<PixelAccountResponse> pixelAccounts = null;
        if (subDomain.getPixelAccounts() != null && !subDomain.getPixelAccounts().isEmpty()) {
            pixelAccounts = subDomain.getPixelAccounts().stream().map(PixelAccountResponse::convertEntity).toList();
        }
        if (pixelAccounts == null || pixelAccounts.isEmpty()) {
            TopLevelDomain parentDomain = subDomain.getParentDomain();
            if (parentDomain != null && parentDomain.getPixelAccounts() != null && !parentDomain.getPixelAccounts().isEmpty()) {
                pixelAccounts = parentDomain.getPixelAccounts().stream().map(PixelAccountResponse::convertEntity).toList();
            }
        }
        return filling(subDomain, SubDomainResponse.builder()
                .name(subDomain.getName())
                .fullName(subDomain.getFullName())
                .redirectDomain(subDomain.getRedirectDomain() == null ? null : convertEntity(subDomain.getRedirectDomain()))
                .parentDomain(TopLevelDomainResponse.convertEntity(subDomain.getParentDomain()))
                .website(WebsiteResponse.convertEntity(subDomain.getWebsite()))
                .theme(ThemeCustomResponse.convertEntity(subDomain.getTheme()))
                .pixels(pixelAccounts)
                .country(CountryResponse.convertEntity(subDomain.getCountry()))
                .currency(CurrencyResponse.convertEntity(subDomain.getCurrency()))
                .language(LanguageResponse.convertEntity(subDomain.getLanguage()))
                .build());
    }
}
