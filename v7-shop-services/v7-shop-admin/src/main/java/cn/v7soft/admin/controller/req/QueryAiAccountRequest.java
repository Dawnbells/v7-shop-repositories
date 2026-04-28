package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.AiApiChannel;
import cn.v7soft.dao.enums.AiProvider;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryAiAccountRequest extends BasePageRequest {
    private String name;
    private AiProvider provider;
    private AiApiChannel apiChannel;
    private Boolean enabled;
}
