package cn.v7soft.dao.entities.primary;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * SSL证书嵌入式类。
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("status <> 'DELETED'")
public class SSLCertificate {
    /**
     * 是否完整执行，false表示超时
     */
    @Column(name = "is_completed")
    private boolean isCompleted;
    /**
     * 是否执行成功
     */
    @Column(name = "is_success")
    private boolean isSuccess;
    /**
     * 是否执行过程中有异常
     */
    @Column(name = "is_error")
    private boolean isError;
    /**
     * 执行结果
     */
    @Column(name = "result", length = 2048)
    private String result;
    /**
     * 执行失败消息， cmd控制台返回的错误消息
     */
    @Column(name = "error_msg", length = 2048)
    private String errorMsg;
    /**
     * 失败日志, json格式，从日志文件中提取的具体错误类型
     */
    @Column(name = "err_log", length = 2048)
    private String errLog;
    /**
     * 证书推送信息
     */
    @Column(name = "ssl_push_msg", length = 2048)
    private String sslPushMsg;

    /**
     * 证书的有效期。
     */
    @Column(name = "certificate_expiry_date")
    private LocalDateTime certificateExpiryDate;
}
