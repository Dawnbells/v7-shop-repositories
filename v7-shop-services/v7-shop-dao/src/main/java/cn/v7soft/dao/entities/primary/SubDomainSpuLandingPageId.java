package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.enums.LandingPageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 子域名SPU落地页关联的复合主键
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubDomainSpuLandingPageId implements Serializable {
    private Long subDomainId;
    private Long spuId;
    private LandingPageType landingPageType;
}
