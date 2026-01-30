package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelAccountState;
import cn.v7soft.dao.enums.PixelTrackingType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于查询像素账号信息的请求类，支持分页和条件查询。
 */
@Getter
@Setter
public class QueryPixelAccountRequest extends BasePageRequest {

    @Schema(title = "像素名称", example = "My Pixel Account")
    private String title;

    @Schema(title = "平台", example = "FACEBOOK")
    private PixelAccountPlatform platform;

    @Schema(title = "状态", example = "ACTIVE")
    private PixelAccountState state;

    @Schema(title = "追踪类型", example = "STANDARD")
    private PixelTrackingType trackingType;

    @Schema(title = "网站ID", example = "1")
    private Long websiteId;
}
