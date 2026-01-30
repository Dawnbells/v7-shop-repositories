package cn.v7soft.dao.entities.base;

import cn.v7soft.dao.entities.primary.Website;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
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
public class BaseWebsiteEntity extends BaseDataRangeEntity {
    /**
     * 数据归属网站
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "website_id")
    private Website website;
}
