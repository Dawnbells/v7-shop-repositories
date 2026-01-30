package cn.v7soft.dao.entities.meta;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceDataMeta {

    /**
     * 页面首次进入的时间戳（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long enterTime = -1;

    /**
     * 页面加载完成后的时间戳（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long exitTime = -1;

    /**
     * 用户在页面上的停留时间（即退出时间 - 进入时间）
     */
    @Builder.Default
    @Column(nullable = false)
    private long dwellTime = -1;

    /**
     * DNS解析耗时（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long dnsTime = -1;

    /**
     * TCP连接耗时（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long tcpTime = -1;

    /**
     * SSL握手耗时（毫秒），若非HTTPS则为0
     */
    @Builder.Default
    @Column(nullable = false)
    private long sslTime = 0;

    /**
     * 首包时间 TTFB（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long ttfb = -1;

    /**
     * DOM加载完成时间（从 fetchStart 开始，单位毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long domLoadTime = -1;

    /**
     * 页面完全加载时间（从 fetchStart 开始，单位毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long pageLoadTime = -1;

    /**
     * 从 navigationStart 到 loadEventEnd 的总耗时（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long totalLoadTime = -1;

    /**
     * FCP（首次内容绘制）的时间戳（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long fcp = -1;

    /**
     * LCP（最大内容绘制）的时间戳（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long lcp = -1;

    /**
     * FID（首次输入延迟）时间（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long fid = -1;

    /**
     * CLS（累计布局偏移）的得分，反映页面稳定性
     */
    @Builder.Default
    @Column(nullable = false)
    private double cls = -1;

    /**
     * 页面加载完成的时间戳（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long onloadTime = -1;

    /**
     * 页面 DOMContentLoaded 完成的时间戳（毫秒）
     */
    @Builder.Default
    @Column(nullable = false)
    private long onDOMContentLoadedTime = -1;
}
