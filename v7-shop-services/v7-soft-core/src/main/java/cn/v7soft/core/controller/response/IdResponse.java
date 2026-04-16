package cn.v7soft.core.controller.response;

import cn.hutool.core.codec.Base62;
import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.core.enums.StatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class IdResponse {
    @Schema(title = "ID", example = "1", description = "ID")
    private String id;
    @Schema(title = "ID序列号", example = "C3kHj4Tc", description = "ID的Base62格式")
    private String compactId;
    @Schema(title = "状态", example = "VALID", description = "状态")
    private StatusEnum status;

    public static  <M extends BaseEntity, N extends IdResponse> N filling(M t, N r) {
        r.setId(String.valueOf(t.getId()));
        r.setCompactId(Base62.encode(String.valueOf(t.getId())));
        r.setStatus(t.getStatus());
        return r;
    }
}
