package cn.v7soft.admin.service.email;

public enum EmailSmtpMode {
    DEPARTMENT_INHERITANCE,
    COMPANY_UNIFIED;

    public static EmailSmtpMode from(String value) {
        if (value == null || value.isBlank()) {
            return DEPARTMENT_INHERITANCE;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return DEPARTMENT_INHERITANCE;
        }
    }
}
