package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询SPU请求类。
 */
@Getter
@Setter
public class QuerySpuRequest extends BasePageRequest {
    // Additional query parameters can be added here
    private Boolean onlyWebsite;

    private String title;
}
