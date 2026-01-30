package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryTopLevelDomainRequest extends BasePageRequest {
    // 其他自定义查询和过滤条件
    private String title;
}
