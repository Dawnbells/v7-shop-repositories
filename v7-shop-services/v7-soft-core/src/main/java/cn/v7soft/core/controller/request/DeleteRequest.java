package cn.v7soft.core.controller.request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.v7soft.core.enums.ClientResponseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeleteRequest {
    @NotEmpty(message = "ID列表不能为空")
    @Schema(title = "ID", example = "1", description = "有id参数表示编辑或者删除，否则表示新增", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ids;

    public List<Long> getIdList() {
        try {
            return Arrays.stream(getIds().split(",")).map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("IDS参数错: " + getIds());
        }
        return new ArrayList<>();
    }
}
