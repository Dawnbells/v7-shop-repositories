package cn.v7soft.dao.entities.primary;

import java.util.ArrayList;
import java.util.List;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelAccountState;
import cn.v7soft.dao.enums.PixelTrackingType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 像素账号实体类，代表一个用于追踪的像素账号。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_pixel_accounts")
public class PixelAccount extends BaseDataRangeEntity {

    /**
     * 像素名称
     */
    @Column(name = "pixel_name", length = 128, nullable = false)
    private String pixelName;

    /**
     * 像素ID
     */
    @Column(name = "pixel_id", length = 128, nullable = false)
    private String pixelId;

    /**
     * 像素AccessToken
     */
    @Column(name = "access_token", length = 256, nullable = false)
    private String accessToken;

    /**
     * 像素账号所属平台
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 32, nullable = false)
    private PixelAccountPlatform platform;

    /**
     * 状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 32, nullable = false)
    private PixelAccountState state;

    /**
     * 追踪类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", length = 32, nullable = false)
    private PixelTrackingType trackingType;

    /**
     * FB购买转化事件
     */
    @Column(name = "conversion_event", length = 32, nullable = false)
    private String conversionEvent;

    /**
     * 嵌入像素 HTML 代码
     */
    @Column(name = "embed_code", columnDefinition = "TEXT")
    private String embedCode;

    /**
     * 所属网站
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "website_id", referencedColumnName = "id")
    private Website website;

    @JsonIgnore
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_spu_pixels",
            joinColumns = @JoinColumn(name = "pixel_id"),
            inverseJoinColumns = @JoinColumn(name = "spu_id")
    )
    private List<Spu> spuList = new ArrayList<>();

}
