package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryProtocolRequest extends BasePageRequest {
    private String title;
}
