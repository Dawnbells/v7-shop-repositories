package cn.v7soft.dao.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NginxConfigType {
    THYMELEAF("xyzdwd-frontend-service"),
    VIKE("xyzdwd-mall-service"),
    NUXT_MALL("nuxt-mall-service");

    private final String upstream;
}
