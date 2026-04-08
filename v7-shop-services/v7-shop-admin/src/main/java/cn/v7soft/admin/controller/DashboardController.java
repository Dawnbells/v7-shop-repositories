package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.resp.DashboardStatsResponse;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "首页统计")
@RequestMapping("/dashboard")
public class DashboardController {

    private final OrderRepository orderRepository;
    private final AiTokenUsageRecordRepository aiTokenUsageRecordRepository;
    private final SystemUserRepository systemUserRepository;

    @SaCheckLogin
    @GetMapping("/stats")
    @Operation(summary = "获取首页统计数据")
    public DashboardStatsResponse getStats() {
        SystemUserDto currentUser = SaSessionUtil.getLoginUser();
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        boolean isCompanyWide = currentUser.getUserType() == SystemUserType.ADMIN
                || currentUser.getUserType() == SystemUserType.COMPANY_ADMIN;

        long orderCount;
        BigDecimal salesAmount;
        int aiCreditsUsed;
        int frozenCredits;

        if (isCompanyWide) {
            orderCount = orderRepository.countOrdersAfter(todayStart);
            salesAmount = orderRepository.sumSalesAfter(todayStart);
            aiCreditsUsed = aiTokenUsageRecordRepository.sumBusinessCreditsAfter(todayStart);
            frozenCredits = systemUserRepository.sumFrozenCreditsByCompanyId(currentUser.getCompanyId());
        } else {
            List<Long> ownerIds = resolveOwnerIds(currentUser);

            if (ownerIds.isEmpty()) {
                return DashboardStatsResponse.builder()
                        .todayOrderCount(0)
                        .todaySalesAmount(BigDecimal.ZERO)
                        .todayAiCreditsUsed(0)
                        .currentAiFrozenCredits(0)
                        .build();
            }

            orderCount = orderRepository.countOrdersAfterByOwners(todayStart, ownerIds);
            salesAmount = orderRepository.sumSalesAfterByOwners(todayStart, ownerIds);
            aiCreditsUsed = aiTokenUsageRecordRepository.sumBusinessCreditsAfterByOwners(todayStart, ownerIds);
            frozenCredits = systemUserRepository.sumFrozenCreditsByUserIds(ownerIds);
        }

        return DashboardStatsResponse.builder()
                .todayOrderCount(orderCount)
                .todaySalesAmount(salesAmount)
                .todayAiCreditsUsed(aiCreditsUsed)
                .currentAiFrozenCredits(frozenCredits)
                .build();
    }

    private List<Long> resolveOwnerIds(SystemUserDto currentUser) {
        SystemUserType userType = currentUser.getUserType();

        if (userType == SystemUserType.EMPLOYEE) {
            return List.of(currentUser.getLongId());
        }

        List<Long> departmentIds = new ArrayList<>();

        switch (userType) {
            case DEPARTMENT_MANAGER:
                if (currentUser.getDepartmentId() != null) {
                    departmentIds.add(currentUser.getDepartmentId());
                }
                break;
            case DEEP_DEPARTMENT_MANAGER:
                if (currentUser.getAccessDepartmentIds() != null) {
                    departmentIds.addAll(currentUser.getAccessDepartmentIds());
                }
                break;
            case DEPARTMENT_TREE:
                if (currentUser.getAccessDepartmentIds() != null) {
                    departmentIds.addAll(currentUser.getAccessDepartmentIds());
                }
                if (currentUser.getParentDepartmentIds() != null) {
                    departmentIds.addAll(currentUser.getParentDepartmentIds());
                }
                break;
            default:
                return List.of(currentUser.getLongId());
        }

        if (departmentIds.isEmpty()) {
            return List.of(currentUser.getLongId());
        }

        List<Long> userIds = systemUserRepository.findUserIdsByDepartmentIds(departmentIds);
        if (userIds == null || userIds.isEmpty()) {
            return List.of(currentUser.getLongId());
        }
        return userIds;
    }
}
