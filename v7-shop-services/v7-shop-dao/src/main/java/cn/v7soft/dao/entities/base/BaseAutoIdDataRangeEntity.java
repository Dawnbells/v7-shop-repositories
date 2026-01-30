package cn.v7soft.dao.entities.base;

import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 与 BaseDataRangeEntity 等价但使用自增 ID 的基类
 */
@Getter
@Setter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("unused")
public abstract class BaseAutoIdDataRangeEntity extends BaseCommonEntity {
    /**
     * 数据归属用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private SystemUser owner;

    /**
     * 填充归属用户（仅在新建且未设置归属用户时填充）
     */
    public <T extends BaseAutoIdDataRangeEntity> T fillOwner() {
        if ((getId() == null || getId() <= 0L) && getOwner() == null) {
            setOwner(SaSessionUtil.getLoginOwner());
        }
        return (T) this;
    }
}
