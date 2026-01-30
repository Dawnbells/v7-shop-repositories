package cn.v7soft.admin.events.listener;

import cn.v7soft.admin.events.event.MultimediaFileSyncEvent;
import cn.v7soft.admin.service.IMultimediaFileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@AllArgsConstructor
public class MultimediaFileSyncListener {
    private final IMultimediaFileService multimediaFileService;

    @EventListener
    @Transactional
    @Async("syncMultimediaFileSyncExecutor")
    public void syncMultimediaFiles(MultimediaFileSyncEvent event) {
//        multimediaFileService.sync();
    }
}
