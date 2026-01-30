package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class MultimediaInfoResponse extends IdResponse {
    private String relativePath;
    private String suffix;
    private String authUrl;
    private LocalDateTime expiredDateTime;
}
