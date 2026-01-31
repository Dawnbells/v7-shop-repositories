package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.ShareType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "查询主题模板请求")
public class QueryThemeTemplateRequest extends BasePageRequest {
    
    @Schema(title = "模板名称，模糊匹配")
    private String name;
    
    @Schema(title = "共享类型")
    private ShareType shareType;
}
