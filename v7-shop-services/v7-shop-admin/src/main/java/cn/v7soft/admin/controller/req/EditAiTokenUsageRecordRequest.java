package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 仅满足 BaseController 泛型约束，AiTokenUsageRecord 为只读记录，不实际使用编辑功能。
 */
@Getter
@Setter
@NoArgsConstructor
public class EditAiTokenUsageRecordRequest extends IdRequest {
}
