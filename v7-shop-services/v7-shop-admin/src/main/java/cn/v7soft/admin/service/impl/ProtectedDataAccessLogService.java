package cn.v7soft.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import cn.v7soft.admin.service.IProtectedDataAccessLogService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ProtectedDataAccessLog;
import cn.v7soft.dao.repositories.primary.ProtectedDataAccessLogRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 受保护客户数据访问日志。
 * <p>
 * 同步写入：异步线程拿不到当前租户（TenantContext）与 sa-token 会话；写入失败只记日志，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtectedDataAccessLogService implements IProtectedDataAccessLogService {

    private final ProtectedDataAccessLogRepository repository;

    @Override
    public void record(String action, String orderType, List<Long> orderIds) {
        record(action, orderType, orderIds, null);
    }

    @Override
    public void record(String action, String orderType, List<Long> orderIds, Long totalCount) {
        try {
            if (orderIds == null) {
                return;
            }
            List<Long> ids = orderIds.stream().filter(Objects::nonNull).toList();
            if (ids.isEmpty()) {
                return;
            }
            long count = totalCount == null ? ids.size() : Math.max(totalCount, ids.size());
            String storedIds = ids.stream()
                    .limit(MAX_STORED_IDS)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            SystemUserDto user = SaSessionUtil.getLoginUser();
            ProtectedDataAccessLog accessLog = ProtectedDataAccessLog.builder()
                    .userId(user == null ? null : user.getLongId())
                    .userName(user == null ? null : user.getName())
                    .action(action)
                    .orderType(orderType)
                    .orderIds(storedIds)
                    .orderCount((int) Math.min(count, Integer.MAX_VALUE))
                    .accessTime(LocalDateTime.now())
                    .build();
            repository.save(accessLog);
        } catch (Exception e) {
            log.warn("受保护数据访问日志写入失败: action={}, orderType={}, size={}",
                    action, orderType, orderIds == null ? 0 : orderIds.size(), e);
        }
    }
}
