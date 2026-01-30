package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EditCloakInfoRequest extends IdRequest {
    @Schema(title = "包含的国家代码，逗号分割", example = "CN")
    private String includeCountryCode;
    @Schema(title = "排除的国家代码，逗号分割", example = "CN")
    private String excludeCountryCode;
    @Schema(title = "包含的爬虫列表", example = "CN")
    private List<String> includeCrawler = new ArrayList<>();
    @Schema(title = "排除的爬虫列表", example = "FacebookBot")
    private List<String> excludeCrawler = new ArrayList<>();
    @Schema(title = "匹配后显示的SPU", example = "GoogleBot")
    private Long spuId;
    @Schema(title = "斗篷规则名称", example = "A页")
    private String name;
}
