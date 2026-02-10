package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 子域名SPU详情响应
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "子域名SPU详情响应")
public class SubDomainSpuDetailResponse {

    @Schema(title = "真实落地页SPU")
    private SpuSimpleWithCountryResponse realLandingPageSpu;

    @Schema(title = "风险用户落地页SPU")
    private SpuSimpleWithCountryResponse riskUserLandingPageSpu;

    @Schema(title = "黑名单落地页SPU")
    private SpuSimpleWithCountryResponse blacklistLandingPageSpu;

    @Schema(title = "主题信息")
    private ThemeSimpleResponse theme;

    @Schema(title = "像素列表")
    private List<PixelSimpleResponse> pixels;

    @Schema(title = "主题编辑器访问地址")
    private String themeEditorUrl;

    @Schema(title = "真实落地页协议信息")
    private ProtocolInfo realLandingPageProtocol;

    @Schema(title = "风险用户落地页协议信息")
    private ProtocolInfo riskUserLandingPageProtocol;

    @Schema(title = "黑名单落地页协议信息")
    private ProtocolInfo blacklistLandingPageProtocol;

    /**
     * 协议信息内部类
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "协议信息")
    public static class ProtocolInfo {
        @Schema(title = "协议ID")
        private Long protocolId;

        @Schema(title = "协议名称")
        private String protocolName;

        @Schema(title = "占位符值")
        private Map<String, String> placeholderValues;
    }
}

