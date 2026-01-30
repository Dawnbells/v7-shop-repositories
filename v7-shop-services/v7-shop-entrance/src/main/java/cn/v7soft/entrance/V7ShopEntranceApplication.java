package cn.v7soft.entrance;

import com.dtflys.forest.springboot.annotation.ForestScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {"cn.v7soft"})
@ForestScan(basePackages = "cn.v7soft.common.forest")
public class V7ShopEntranceApplication {

    public static void main(String[] args) {
        SpringApplication.run(V7ShopEntranceApplication.class, args);
    }
}
