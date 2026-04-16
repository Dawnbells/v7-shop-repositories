package cn.v7soft.core.controller.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdRequest {

    @Nullable
    @Pattern(regexp = "^[0-9]+$", message = "ID不正确")
    @Schema(title = "ID", example = "1", description = "有id参数表示编辑或者删除，否则表示新增", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    public Long getIdLongValue() {
        Objects.requireNonNull(id, "ID不能为空");
        return Long.parseLong(id);
    }

    public boolean hasId() {
        return StringUtils.hasText(id);
    }
}
