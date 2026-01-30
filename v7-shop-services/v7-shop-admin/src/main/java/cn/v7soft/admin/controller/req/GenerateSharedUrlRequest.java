package cn.v7soft.admin.controller.req;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateSharedUrlRequest {
    private String url;
    private long expireSeconds;
}
