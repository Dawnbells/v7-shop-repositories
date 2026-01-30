package cn.v7soft.dao.entities.meta;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
public class OrderFinancialInfo {

    /**
     * 订单总金额
     */
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    /**
     * 运费
     */
    @Column(name = "shipping_fee", nullable = false)
    private BigDecimal shippingFee;

    /**
     * 折扣金额
     */
    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    /**
     * 税费
     */
    @Column(name = "tax_amount")
    private BigDecimal taxAmount;
}
