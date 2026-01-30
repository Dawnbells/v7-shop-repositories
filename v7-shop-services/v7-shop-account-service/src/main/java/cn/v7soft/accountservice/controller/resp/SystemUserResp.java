package cn.v7soft.accountservice.controller.resp;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@SuperBuilder
public class SystemUserResp {
    private String username;
    private String avatar;
    @Builder.Default
    private List<String> roles = new ArrayList<>();
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
}
