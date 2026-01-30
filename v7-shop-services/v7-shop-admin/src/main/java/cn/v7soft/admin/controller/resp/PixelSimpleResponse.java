package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 像素简单响应
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "像素简单信息响应")
public class PixelSimpleResponse {

    @Schema(title = "像素账号ID")
    private String id;

    @Schema(title = "像素名称")
    private String pixelName;

    @Schema(title = "像素ID")
    private String pixelId;

    @Schema(title = "平台")
    private PixelAccountPlatform platform;

    @Schema(title = "转化事件")
    private String conversionEvent;

    public static PixelSimpleResponse convertEntity(PixelAccount pixelAccount) {
        if (pixelAccount == null) {
            return null;
        }
        return PixelSimpleResponse.builder()
                .id(String.valueOf(pixelAccount.getId()))
                .pixelName(pixelAccount.getPixelName())
                .pixelId(pixelAccount.getPixelId())
                .platform(pixelAccount.getPlatform())
                .conversionEvent(pixelAccount.getConversionEvent())
                .build();
    }
}

