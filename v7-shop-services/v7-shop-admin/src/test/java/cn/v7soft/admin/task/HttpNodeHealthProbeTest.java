package cn.v7soft.admin.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class HttpNodeHealthProbeTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsMallHealthResponse() throws Exception {
        start(exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));

        HealthProbeResult result = new HttpNodeHealthProbe(1000, 1000)
                .probe("127.0.0.1:" + server.getAddress().getPort());

        assertTrue(result.healthy());
    }

    @Test
    void rejectsRedirectWithoutFollowingIt() throws Exception {
        start(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1/health");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        HealthProbeResult result = new HttpNodeHealthProbe(1000, 1000)
                .probe("127.0.0.1:" + server.getAddress().getPort());

        assertFalse(result.healthy());
        assertTrue(result.detail().contains("302"));
    }

    @Test
    void rejectsUnexpectedJsonBody() throws Exception {
        start(exchange -> respond(exchange, 200, "{\"status\":\"starting\"}"));

        HealthProbeResult result = new HttpNodeHealthProbe(1000, 1000)
                .probe("127.0.0.1:" + server.getAddress().getPort());

        assertFalse(result.healthy());
    }

    private void start(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> handler.handle(exchange));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
