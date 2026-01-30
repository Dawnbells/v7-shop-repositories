package cn.v7soft.dao.entities.base;

import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 数据范围查询
 */
@Getter
@Setter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDataRangeEntity extends cn.v7soft.dao.entities.base.BaseTenantEntity implements IBaseDataRangeEntity {
    /**
     * 数据归属用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private SystemUser owner;

    public <T extends BaseDataRangeEntity> T fillOwner() {
        if ((getId() == null || getId() <= 0L) && getOwner() == null) {
            setOwner(SaSessionUtil.getLoginOwner());
        }
        //noinspection unchecked
        return (T) this;
    }
}
