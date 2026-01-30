package cn.v7soft.admin.controller.req;

import java.util.List;

import cn.v7soft.core.controller.request.IdRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EditProtocolTranslationRequest extends IdRequest {
    private List<ArticleGroupRequest> articleGroupList;
}
