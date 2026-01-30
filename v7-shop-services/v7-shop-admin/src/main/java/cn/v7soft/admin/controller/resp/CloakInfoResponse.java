package cn.v7soft.admin.controller.resp;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.CloakInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CloakInfoResponse extends IdResponse {
    @Schema(title = "包含的国家代码，逗号分割", example = "CN")
    private String includeCountryCode;
    @Schema(title = "排除的国家代码，逗号分割", example = "CN")
    private String excludeCountryCode;
    @Builder.Default
    @Schema(title = "包含的爬虫列表", example = "CN")
    private List<String> includeCrawler = new ArrayList<>();
    @Builder.Default
    @Schema(title = "排除的爬虫列表", example = "FacebookBot")
    private List<String> excludeCrawler = new ArrayList<>();
    @Schema(title = "匹配后显示的SPU", example = "GoogleBot")
    private Long spuId;
    @Schema(title = "斗篷规则名称", example = "A页")
    private String name;

    public static CloakInfoResponse convert(CloakInfo cloakInfo) {
        CloakInfoResponseBuilder<?, ?> builder = CloakInfoResponse.builder()
                .id(String.valueOf(cloakInfo.getId()))
                .name(cloakInfo.getName())
                .spuId(cloakInfo.getSpuId())
                .includeCountryCode(cloakInfo.getIncludeCountryCode())
                .excludeCountryCode(cloakInfo.getExcludeCountryCode());
        if (StrUtil.isNotBlank(cloakInfo.getIncludeCrawler())) {
            builder.includeCrawler(Arrays.stream(cloakInfo.getIncludeCrawler().split(",")).toList());
        }
        if (StrUtil.isNotBlank(cloakInfo.getExcludeCrawler())) {
            builder.excludeCrawler(Arrays.stream(cloakInfo.getExcludeCrawler().split(",")).toList());
        }
        return builder.build();
    }
}
