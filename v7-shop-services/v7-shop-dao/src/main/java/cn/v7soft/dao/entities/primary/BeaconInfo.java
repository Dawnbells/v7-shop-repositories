package cn.v7soft.dao.entities.primary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "t_beacon_log")
public class BeaconInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 客户端IP
     */
    @Column(name = "ip", nullable = false, length = 50)
    private String ip;
    /**
     * 域名
     */
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /**
     * 进入时间，通常是时间戳（毫秒）
     */
    @Column(name = "enter_time")
    private Long enterTime;

    /**
     * 离开时间，通常是时间戳（毫秒）
     */
    @Column(name = "exit_time")
    private Long exitTime;

    /**
     * 停留时间
     */
    @Column(name = "dwell_time")
    private Long dwellTime;

    /**
     * 是否已加载内容
     */
    @Column(name = "loaded_content")
    private Boolean loadedContent;

    /**
     * 加载内容用时
     */
    @Column(name = "loaded_content_elapsed")
    private Long loadContentElapsed;
    /**
     * websocket onOpen用时
     */
    @Column(name = "websocket_open_elapsed")
    private Long websocketOpenElapsed;
    /**
     * websocket test ping finish用时
     */
    @Column(name = "websocket_message_elapsed")
    private Long websocketMessageElapsed;
    /**
     * websocket on close 用时
     */
    @Column(name = "websocket_close_elapsed")
    private Long websocketCloseElapsed;
    /**
     * websocket错误用时
     */
    @Column(name = "websocket_error_elapsed")
    private Long websocketErrorElapsed;
    @Column(name = "ws_val")
    private String wsVal;
    @Column(name = "pd_key")
    private String pdKey;
    @Column(name = "pd_val")
    private String pdVal;

    /**
     * 网站ID
     */
    @Column(name = "website_id")
    private Long websiteId;

    /**
     * 国家ID
     */
    @Column(name = "country_id")
    private Long countryId;

    /**
     * 子域名ID
     */
    @Column(name = "subdomain_id")
    private Long subdomainId;

}
