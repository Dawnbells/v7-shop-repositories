package cn.v7soft.dao.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NginxConfigType {
    THYMELEAF("xyzdwd-frontend-service"),
    VIKE("xyzdwd-mall-service");

    private final String upstream;
}
