package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckSpuTicketRequest extends IdRequest {
    @Schema(title = "ticket", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ticket;
}
