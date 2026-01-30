package cn.v7soft.admin.controller.resp;

import java.util.List;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelAccountState;
import cn.v7soft.dao.enums.PixelTrackingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 用于返回像素账号信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "像素账号信息响应")
public class PixelAccountResponse extends DataRangeResponse {

    @Schema(title = "像素名称", example = "My Pixel Account")
    private String pixelName;

    @Schema(title = "像素ID", example = "123456789")
    private String pixelId;

    @Schema(title = "AccessToken", example = "abcdefg123456789")
    private String accessToken;

    @Schema(title = "像素平台", example = "FACEBOOK")
    private PixelAccountPlatform platform;

    @Schema(title = "像素状态", example = "ACTIVE")
    private PixelAccountState state;

    @Schema(title = "追踪类型", example = "STANDARD")
    private PixelTrackingType trackingType;

    @Schema(title = "追踪商品列表", example = "STANDARD")
    private List<SpuSimpleResponse> spuList;
    /**
     * FB购买转化事件
     */
    private String conversionEvent;

    /**
     * 从 `PixelAccount` 实体转换为 `PixelAccountResponse` 的静态方法。
     */
    public static PixelAccountResponse convertEntity(PixelAccount pixelAccount) {
        List<Spu > spuList = pixelAccount.getSpuList() == null ? List.of() : pixelAccount.getSpuList();
        List<SpuSimpleResponse> simpleResponses = spuList.stream().map(SpuSimpleResponse::convertEntity).toList();
        return filling(pixelAccount, PixelAccountResponse.builder()
                .pixelName(pixelAccount.getPixelName())
                .pixelId(pixelAccount.getPixelId())
                .accessToken(pixelAccount.getAccessToken())
                .platform(pixelAccount.getPlatform())
                .state(pixelAccount.getState())
                .trackingType(pixelAccount.getTrackingType())
                .spuList(simpleResponses)
                .conversionEvent(pixelAccount.getConversionEvent())
                .build());
    }
}
