package cn.v7soft.dao.entities.meta;

import cn.v7soft.dao.enums.PaymentMethod;
import cn.v7soft.dao.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
public class OrderPaymentInfo {

    /**
     * 支付方式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50, nullable = false)
    private PaymentMethod paymentMethod;

    /**
     * 支付状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 50, nullable = false)
    private PaymentStatus paymentStatus;

    /**
     * 支付时间
     */
    @Column(name = "payment_time")
    private LocalDateTime paymentTime;
}
