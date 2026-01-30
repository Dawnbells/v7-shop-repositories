package cn.v7soft.admin.events.event;

import org.springframework.context.ApplicationEvent;

import java.time.Clock;

/**
 *多媒体文件同步事件
 */
public class MultimediaFileSyncEvent extends ApplicationEvent {
    public MultimediaFileSyncEvent(Object source) {
        super(source);
    }

    public MultimediaFileSyncEvent(Object source, Clock clock) {
        super(source, clock);
    }
}
