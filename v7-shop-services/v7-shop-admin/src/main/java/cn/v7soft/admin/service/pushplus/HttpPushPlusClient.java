package cn.v7soft.admin.service.pushplus;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpPushPlusClient implements PushPlusClient {

    private static final String SEND_URL = "http://www.pushplus.plus/send";
    private static final int TIMEOUT_MS = 5000;

    @Override
    public PushPlusSendResponse send(PushPlusSendRequest request) {
        try (HttpResponse response = HttpRequest.post(SEND_URL)
                .contentType("application/json")
                .body(JSONUtil.toJsonStr(request))
                .timeout(TIMEOUT_MS)
                .execute()) {
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return PushPlusSendResponse.builder()
                        .code(response.getStatus())
                        .msg("PushPlus HTTP 请求失败")
                        .build();
            }
            return JSONUtil.toBean(response.body(), PushPlusSendResponse.class);
        } catch (Exception e) {
            log.warn("PushPlus 通知请求异常: {}", e.getMessage());
            return PushPlusSendResponse.builder()
                    .code(-1)
                    .msg("PushPlus 通知请求异常")
                    .build();
        }
    }
}
