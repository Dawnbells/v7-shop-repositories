package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class CountThirdPartyOrderResponse extends IdResponse {
    private int count;
}
