package cn.v7soft.dao.entities.primary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 子域名SPU像素关联实体类
 * 存储子域名、SPU和像素的三方关联关系
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_sub_domain_spu_pixels")
@IdClass(SubDomainSpuPixelId.class)
public class SubDomainSpuPixel {

    @Id
    @Column(name = "sub_domain_id")
    private Long subDomainId;

    @Id
    @Column(name = "spu_id")
    private Long spuId;

    @Id
    @Column(name = "pixel_id")
    private Long pixelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_domain_id", insertable = false, updatable = false)
    private SubDomain subDomain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spu_id", insertable = false, updatable = false)
    private Spu spu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pixel_id", insertable = false, updatable = false)
    private PixelAccount pixelAccount;
}

