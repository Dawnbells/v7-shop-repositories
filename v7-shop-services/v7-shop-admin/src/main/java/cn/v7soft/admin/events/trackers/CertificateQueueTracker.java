package cn.v7soft.admin.events.trackers;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 证书申请队列顺序跟踪器。
 * <p>
 * 忠实镜像 certificateRequestAsyncExecutor 的 FIFO 提交顺序，仅用于展示「队列中第N位」，
 * 不参与实际调度。仅记录证书申请域名，天然排除同执行器上的 nginx 刷新任务。
 */
@Component
public class CertificateQueueTracker {

    /** 去重 + 保持首次入队顺序 */
    private final LinkedHashSet<Long> queue = new LinkedHashSet<>();

    /** 入队：发布证书申请事件前调用，追加到队尾（已存在则保持原位）。 */
    public synchronized void enqueue(Long domainId) {
        queue.add(domainId);
    }

    /** 出队：监听器开始处理（域名转为 REQUESTING）时调用。 */
    public synchronized void remove(Long domainId) {
        queue.remove(domainId);
    }

    /** 一次性快照：domainId -> 第几位（从 1 开始），供分页批量查询。 */
    public synchronized Map<Long, Integer> positionSnapshot() {
        Map<Long, Integer> snapshot = new HashMap<>();
        int pos = 1;
        for (Long id : queue) {
            snapshot.put(id, pos++);
        }
        return snapshot;
    }
}
