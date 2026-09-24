package cn.v7soft.admin.task.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按来源 IP 限制同时回传的译图数量。
 * <p>
 * 同一台电脑上多个 Chrome 用户配置各跑一个插件，彼此隔离无法协调，但出口 IP 相同，
 * 由服务端按 IP 发放上传名额即可把它们归成一组，共享这台设备的上行带宽。
 * 插件必须"先领名额再传大图"：nginx 默认收完整个请求体才转发，等 complete 到了再拒绝，
 * 几 MB 的上行已经花掉了。领不到名额的译图留在插件里等，靠心跳保住 assignment 租约。
 * <p>
 * 每次领取都发一个新的 slotId，complete 带着它来归还：同一张图超时重投时旧请求晚到，
 * 只会归还旧 slotId，不会把重投刚领到的名额让出去。插件崩溃或请求体没传完时靠租约到期回收。
 */
@Slf4j
@Component
public class TurboFlowUploadGate {

    /** 名额租约：覆盖插件单次回传超时（120s）并留余量。 */
    static final Duration SLOT_LEASE = Duration.ofSeconds(150);

    private final int slotsPerIp;
    private final Clock clock;
    // ip -> (assignmentId -> 名额)，全部访问在 this 上同步；规模只有几路，不值得做无锁结构
    private final Map<String, Map<String, Slot>> slotsByIp = new HashMap<>();

    private record Slot(String id, Instant expiresAt) {
    }

    @Autowired
    public TurboFlowUploadGate(@Value("${application.turboflow.upload-slots-per-ip:3}") int slotsPerIp) {
        this(slotsPerIp, Clock.systemUTC());
    }

    TurboFlowUploadGate(int slotsPerIp, Clock clock) {
        this.slotsPerIp = Math.max(1, slotsPerIp);
        this.clock = clock;
    }

    /** 领取一个上传名额，返回 slotId；该 IP 名额已满时返回 null，插件稍后再领。 */
    public synchronized String tryAcquire(String ip, String assignmentId) {
        Instant now = clock.instant();
        purgeExpired(now);
        Map<String, Slot> slots = slotsByIp.computeIfAbsent(ip, k -> new HashMap<>());
        // 同一张图重投沿用它的位置，不额外占名额；但换新 slotId，旧请求就还不掉它
        if (!slots.containsKey(assignmentId) && slots.size() >= slotsPerIp) {
            log.debug("[TurboFlowUploadGate] upload slot busy: ip={}, assignmentId={}, active={}",
                    ip, assignmentId, slots.size());
            return null;
        }
        Slot slot = new Slot(UUID.randomUUID().toString(), now.plus(SLOT_LEASE));
        slots.put(assignmentId, slot);
        return slot.id();
    }

    /**
     * 回传处理结束后归还名额。slotId 为空时（fail 上报、老插件）按 assignmentId 无条件归还；
     * 否则只归还同一次领取的名额。按 assignmentId 查找，出口 IP 中途变化也能还掉。
     */
    public synchronized void release(String assignmentId, String slotId) {
        if (assignmentId == null) {
            return;
        }
        Iterator<Map<String, Slot>> it = slotsByIp.values().iterator();
        while (it.hasNext()) {
            Map<String, Slot> slots = it.next();
            Slot slot = slots.get(assignmentId);
            if (slot != null && (slotId == null || slotId.equals(slot.id()))) {
                slots.remove(assignmentId);
            }
            if (slots.isEmpty()) {
                it.remove();
            }
        }
    }

    /** 清掉所有 IP 的过期名额：换了出口 IP 或崩溃的插件不会再来领，只清当前 IP 会让它们永远留在内存里。 */
    private void purgeExpired(Instant now) {
        Iterator<Map<String, Slot>> it = slotsByIp.values().iterator();
        while (it.hasNext()) {
            Map<String, Slot> slots = it.next();
            slots.values().removeIf(slot -> !slot.expiresAt().isAfter(now));
            if (slots.isEmpty()) {
                it.remove();
            }
        }
    }

    synchronized int trackedIpCount() {
        return slotsByIp.size();
    }
}
