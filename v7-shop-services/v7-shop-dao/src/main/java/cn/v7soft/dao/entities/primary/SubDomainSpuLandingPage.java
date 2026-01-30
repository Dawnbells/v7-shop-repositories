package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.enums.LandingPageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 子域名SPU落地页关联实体类
 * 存储子域名、SPU和落地页SPU的个性化配置关系
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_sub_domain_spu_landing_pages")
@IdClass(SubDomainSpuLandingPageId.class)
public class SubDomainSpuLandingPage {

    @Id
    @Column(name = "sub_domain_id")
    private Long subDomainId;

    @Id
    @Column(name = "spu_id")
    private Long spuId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "landing_page_type")
    private LandingPageType landingPageType;

    @Column(name = "landing_page_spu_id")
    private Long landingPageSpuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_domain_id", insertable = false, updatable = false)
    private SubDomain subDomain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spu_id", insertable = false, updatable = false)
    private Spu spu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landing_page_spu_id", insertable = false, updatable = false)
    private Spu landingPageSpu;
}
