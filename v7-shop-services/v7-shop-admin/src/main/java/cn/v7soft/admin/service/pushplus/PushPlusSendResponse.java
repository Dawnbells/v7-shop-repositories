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
public class PushPlusSendResponse {

    private Integer code;

    private String msg;

    private String data;
}
