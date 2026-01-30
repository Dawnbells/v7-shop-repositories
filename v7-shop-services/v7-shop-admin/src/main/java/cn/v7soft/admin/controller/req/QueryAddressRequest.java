package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryAddressRequest extends BasePageRequest {

    @Schema(title = "省份", example = "北京市")
    private String province;

    @Schema(title = "城市", example = "北京市")
    private String city;

    @Schema(title = "区", example = "朝阳区")
    private String district;
}
