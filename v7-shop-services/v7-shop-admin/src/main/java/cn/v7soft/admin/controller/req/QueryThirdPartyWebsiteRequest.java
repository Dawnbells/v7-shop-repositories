package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryThirdPartyWebsiteRequest extends BasePageRequest {

    @Schema(title = "令牌", example = "example-token", description = "根据令牌过滤")
    private String token;

    @Schema(title = "应用 Key", example = "example-app-key", description = "根据应用 Key 过滤")
    private String appKey;

    @Schema(title = "认证类型", example = "OAUTH2", description = "根据认证类型过滤")
    private ThirdPartyAuthTypeEnum authType;

    @Schema(title = "创建时间起始范围", example = "2023-01-01T00:00:00", description = "查询在该时间之后创建的记录")
    private String createdAfter;

    @Schema(title = "创建时间结束范围", example = "2023-12-31T23:59:59", description = "查询在该时间之前创建的记录")
    private String createdBefore;
}
