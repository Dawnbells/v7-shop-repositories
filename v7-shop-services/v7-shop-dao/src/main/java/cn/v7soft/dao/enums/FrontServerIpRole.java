package cn.v7soft.dao.enums;

public enum FrontServerIpRole {
    PRIMARY("主IP", 0),
    FAILOVER("备用IP", 1),
    FALLBACK("兜底IP", 2);

    private final String label;
    private final int priority;

    FrontServerIpRole(String label, int priority) {
        this.label = label;
        this.priority = priority;
    }

    public String getLabel() {
        return label;
    }

    public int getPriority() {
        return priority;
    }
}
