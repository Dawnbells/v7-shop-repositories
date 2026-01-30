package cn.v7soft.common.utils;

public interface RegexPattern {
    /**
     * 纯文字
     */
    String REGEX_PLAIN_TEXT = "^[^0-9]+$";
    /**
     * 纯数字
     */
    String REGEX_PURE_NUMBERS = "^\\d+$";
    /**
     * 邮箱正则表达式
     */
    String REGEX_EMAIL = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
}
