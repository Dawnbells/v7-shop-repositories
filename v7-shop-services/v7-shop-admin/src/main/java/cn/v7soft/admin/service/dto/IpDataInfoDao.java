package cn.v7soft.admin.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class IpDataInfoDao {

    /**
     * 请求是否成功
     */
    private boolean success;

    /**
     * 是否是分配或私有的 IP 地址
     */
    private boolean bogon;

    /**
     * 是否是代理（如 VPN、Tor）
     */
    private boolean proxy;

    /**
     * 是否是数据中心 IP
     */
    private boolean datacenter;

    /**
     * 是否是移动网络 IP
     */
    private boolean mobile;

    /**
     * 爬虫类型，NONE 表示非爬虫
     */
    @Builder.Default
    private String crawlerType = "NONE";

    /**
     * IP 地址（如 24.48.0.1）
     */
    private String ip;

    /**
     * 大陆代码（如 NA）
     */
    private String continentCode;

    /**
     * 国家代码（如 CA）
     */
    private String countryCode;

    private String country;

    /**
     * 城市（如 Montreal）
     */
    private String city;

    /**
     * 地区（如 Quebec）
     */
    private String district;

    /**
     * 邮编（如 H3A1A4）
     */
    private String zip;

    /**
     * 纬度（如 45.6085）
     */
    private double latitude;

    /**
     * 经度（如 -73.5493）
     */
    private double longitude;

    /**
     * 时区（如 America/Toronto）
     */
    private String timezone;

    /**
     * 时区偏移量（秒，如 -14400）
     */
    private int timeOffset;

    /**
     * 原始 JSON 响应内容
     */
    @JsonIgnore
    private String rawResponse;

    /**
     * 从请求发起到结束的响应时间（毫秒）
     */
    private long responseTimeMs;
}
