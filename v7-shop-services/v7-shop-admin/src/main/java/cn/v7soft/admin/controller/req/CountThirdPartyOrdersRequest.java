package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CountThirdPartyOrdersRequest extends IdRequest {
    @Nullable
    private LocalDateTime createAtMin;
    @Nullable
    private LocalDateTime createAtMax;
}
