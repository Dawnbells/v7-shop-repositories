package cn.v7soft.admin.task;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cn.hutool.json.JSONUtil;

@Component
public class HttpNodeHealthProbe implements NodeHealthProbe {

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpNodeHealthProbe(
            @Value("${front-server.health-check.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${front-server.health-check.read-timeout-ms:3000}") int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public HealthProbeResult probe(String ipv4) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + ipv4 + "/health");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);

            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                return HealthProbeResult.unhealthy("HTTP " + statusCode);
            }
            try (InputStream input = connection.getInputStream()) {
                String body = new String(input.readNBytes(16 * 1024), StandardCharsets.UTF_8);
                String status = JSONUtil.parseObj(body).getStr("status");
                if (!"ok".equalsIgnoreCase(status)) {
                    return HealthProbeResult.unhealthy("响应 status 不是 ok");
                }
            }
            return HealthProbeResult.healthy("HTTP 200, status=ok");
        } catch (Exception e) {
            String message = e.getMessage();
            return HealthProbeResult.unhealthy(e.getClass().getSimpleName()
                    + (message == null || message.isBlank() ? "" : ": " + message));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
