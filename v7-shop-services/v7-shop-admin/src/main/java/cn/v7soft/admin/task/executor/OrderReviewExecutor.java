package cn.v7soft.admin.task.executor;

import java.util.Optional;

import org.springframework.stereotype.Service;

import cn.v7soft.admin.service.IBotOrderCheckService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.admin.service.dto.TemporaryOrderDto;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewExecutor {

    private final ITemporaryOrderService temporaryOrderService;
    private final IBotOrderCheckService botOrderCheckService;

    // 记录上次无订单日志打印时间（毫秒）
    private volatile long lastNoOrderLogTime = 0;

    public long reviewNext() {
        try {
            TenantContext.silent();
            Optional<TemporaryOrderDto> next = temporaryOrderService.getNextBotPendingOrder();
            if (next.isPresent()) {
                TemporaryOrderDto order = next.get();
                botOrderCheckService.botReviewOrder(order);
                log.debug("✅ 自动审核完成，订单ID: {}", order.getId());
                return 10;
            } else {
                long now = System.currentTimeMillis();
                if (now - lastNoOrderLogTime > 60_000) { // 超过1分钟
                    log.info("⏸ 没有待审核订单，稍后再试");
                    lastNoOrderLogTime = now;
                }
                // 不足一分钟就不输出，静默等待
                return 10000; // 10秒后再检查
            }
        } finally {
            TenantContext.restore();
        }
    }
}
