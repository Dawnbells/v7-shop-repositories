package cn.v7soft.dao.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RepeatType {
    IP("浏览器IP"),
    PHONE("用户手机"),
    NAME("用户姓名"),
    DEVICE("用户设备"),
    REAL_IP("真实IP");

    private final String name;
}
