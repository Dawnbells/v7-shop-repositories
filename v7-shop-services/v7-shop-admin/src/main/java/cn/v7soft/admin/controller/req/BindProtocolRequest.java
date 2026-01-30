package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BindProtocolRequest extends IdRequest {

    @Schema(title = "协议ID")
    private String protocolId;

    @Schema(title = "占位符值", description = "协议模板中的占位符及其对应的值")
    private Map<String, String> placeholderValues;
}
