package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.core.controller.response.IdResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "检查SPU预览响应")
public class CheckSpuTicketResponse extends IdResponse {
    private String ticket;
    private boolean isValid;
}
