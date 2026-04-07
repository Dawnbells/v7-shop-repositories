package cn.v7soft.entrance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.dtflys.forest.springboot.annotation.ForestScan;

@EnableAsync
@EnableCaching
@EnableScheduling
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {"cn.v7soft"})
@ForestScan(basePackages = "cn.v7soft.common.forest")
public class V7ShopEntranceApplication {

    public static void main(String[] args) {

        BufferedReader br = null;
        try {
            URL url = new URL("https://api.ipify.org");
            br = new BufferedReader(new InputStreamReader(url.openStream()));
            System.out.println("Java 当前识别的外网 IP: " + br.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
        SpringApplication.run(V7ShopEntranceApplication.class, args);
    }
}
