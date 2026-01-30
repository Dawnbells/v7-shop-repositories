package cn.v7soft.dao.enums;

import lombok.Getter;

@Getter
public enum Gender {
    MALE("男"),
    FEMALE("女");

    private final String value;

    Gender(String value) {
        this.value = value;
    }
}
