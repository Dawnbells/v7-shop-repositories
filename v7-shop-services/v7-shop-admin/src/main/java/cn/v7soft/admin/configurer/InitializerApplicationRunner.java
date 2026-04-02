package cn.v7soft.admin.configurer;

import javax.imageio.ImageIO;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.utils.ThemeLoader;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class InitializerApplicationRunner implements ApplicationRunner {

    private final ITaskExecutorService taskExecutorService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ImageIO.scanForPlugins();
        ThemeLoader.loadAllThemes();
        log.debug("themes >> {}", ThemeLoader.getThemes());
        taskExecutorService.recoverUnfinishedTasks();
    }

}
