package cn.v7soft.dao.enums;

public enum PaymentStatus {
    WAIT_PAY,
    AUTHORIZED,
    PENDING,
    PARTIALLY_PAID,
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public static PaymentStatus convertFromShopline(String status) {
        String[] paymentStatus = new String[]{"unpaid", "authorized", "pending", "partially_paid", "paid", "partially_refunded", "refunded"};
        PaymentStatus[] paymentStatusEnums = new PaymentStatus[]{WAIT_PAY, AUTHORIZED, PENDING, PARTIALLY_PAID, PAID, PARTIALLY_REFUNDED, REFUNDED};
        for (int i = 0; i < paymentStatus.length; i++) {
            if (paymentStatus[i].equalsIgnoreCase(status)) {
                return paymentStatusEnums[i];
            }
        }
        return PaymentStatus.WAIT_PAY;
    }
}
