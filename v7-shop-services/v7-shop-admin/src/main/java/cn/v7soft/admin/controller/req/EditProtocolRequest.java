package cn.v7soft.admin.controller.req;

import java.util.List;

import cn.v7soft.core.controller.request.IdRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditProtocolRequest extends IdRequest {
    private String name;
    private Long defaultLanguageId;
    private List<Long> languageIds;
}
