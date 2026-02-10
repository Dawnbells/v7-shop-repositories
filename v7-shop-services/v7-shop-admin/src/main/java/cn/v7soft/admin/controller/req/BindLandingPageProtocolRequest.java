package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.LandingPageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "绑定协议到落地页请求")
public class BindLandingPageProtocolRequest {

    @Schema(title = "子域名ID")
    private Long subDomainId;

    @Schema(title = "SPU ID")
    private Long spuId;

    @Schema(title = "落地页类型")
    private LandingPageType landingPageType;

    @Schema(title = "协议ID")
    private String protocolId;

    @Schema(title = "占位符值", description = "协议模板中的占位符及其对应的值")
    private Map<String, String> placeholderValues;
}
