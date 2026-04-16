package cn.v7soft.core.enums;


public enum StatusEnum {
    /**
     * 有效
     */
    VALID("VALID"),
    /**
     * 无效
     */
    INVALID("INVALID"),
    /**
     * 已删除
     */
    DELETED("DELETED");

    private String value;
    StatusEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
