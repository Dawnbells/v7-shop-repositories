package cn.v7soft.admin.configurer;

import javax.imageio.ImageIO;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import cn.v7soft.admin.utils.ConfigCenterLoader;
import cn.v7soft.admin.utils.ThemeLoader;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 初始化域名证书申请
 */
@Slf4j
@Component
@AllArgsConstructor
public class InitializerApplicationRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        ImageIO.scanForPlugins();
        ThemeLoader.loadAllThemes();
        // ConfigCenterLoader 已通过 @PostConstruct 自动初始化
        log.debug("themes >> {}", ThemeLoader.getThemes());
    }

}
