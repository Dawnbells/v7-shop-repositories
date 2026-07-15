package cn.v7soft.dao.enums;

public enum AddressOrder {
    FORWARD,
    REVERSE;

    public static AddressOrder defaultIfNull(AddressOrder addressOrder) {
        return addressOrder == null ? REVERSE : addressOrder;
    }
}
