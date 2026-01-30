package cn.v7soft.dao.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CheckStatus {
    PENDING("待审核"),
    DUPLICATE("只重单"),
    WARNING("只提示"),
    DUPLICATE_WARNING("重单提示"),
    NORMAL("正常单");

    private final String name;
}
