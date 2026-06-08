package cn.v7soft.admin.service.pushplus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushPlusSendRequest {

    private String token;

    private String title;

    private String content;

    private String channel;

    private String template;
}
