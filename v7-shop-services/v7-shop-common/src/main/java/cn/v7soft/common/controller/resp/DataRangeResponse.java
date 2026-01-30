package cn.v7soft.common.controller.resp;

import cn.hutool.core.codec.Base62;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
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
public class DataRangeResponse  extends IdResponse {
    @Schema(title = "归属人")
    private String ownerName;

    @Schema(title = "归属部门")
    private String departmentName;
    protected static  <M extends BaseDataRangeEntity, N extends DataRangeResponse> N filling(M t, N r) {
        SystemUser owner = t.getOwner();
        r.setId(String.valueOf(t.getId()));
        r.setCompactId(Base62.encode(String.valueOf(t.getId())));
        r.setStatus(t.getStatus());
        if (owner != null) {
            r.setOwnerName(owner.getName());
            r.setDepartmentName(owner.getDepartment() != null? owner.getDepartment().getName(): null);
        }
        return r;
    }
}
