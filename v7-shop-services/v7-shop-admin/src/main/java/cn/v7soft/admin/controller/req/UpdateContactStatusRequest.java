package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdsRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateContactStatusRequest extends IdsRequest {
    private Boolean contacted;
}
