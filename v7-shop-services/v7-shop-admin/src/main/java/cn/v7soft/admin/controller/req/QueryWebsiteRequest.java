package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于查询网站信息的请求类，支持分页。
 */
@Getter
@Setter
public class QueryWebsiteRequest extends BasePageRequest {
    private String title;
}
