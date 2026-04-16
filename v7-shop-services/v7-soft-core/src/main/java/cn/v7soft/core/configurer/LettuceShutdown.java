package cn.v7soft.core.configurer;

import io.lettuce.core.resource.DefaultClientResources;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Component;

@Component
public class LettuceShutdown {

    private final DefaultClientResources clientResources;

    public LettuceShutdown(@Nullable DefaultClientResources clientResources) {
        this.clientResources = clientResources;
    }

    @PreDestroy
    public void destroy() {
        if (clientResources != null) {
            clientResources.shutdown();
        }
    }
}
