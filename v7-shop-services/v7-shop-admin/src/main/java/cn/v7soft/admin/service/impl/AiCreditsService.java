package cn.v7soft.admin.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.exception.InsufficientCreditsException;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCreditsService {

    private final SystemUserRepository systemUserRepository;

    /**
     * 冻结指定 credits。
     *
     * @return true=实际执行了冻结, false=用户无限额(monthlyAiCredits==-1)
     * @throws InsufficientCreditsException 额度不足或已禁用(null/0)
     */
    @Transactional
    public boolean freeze(Long userId, int estimated) {
        SystemUser user = systemUserRepository.findById(userId).orElseThrow();
        Integer monthly = user.getMonthlyAiCredits();
        if (user.getUserType() == SystemUserType.ADMIN || user.getUserType() == SystemUserType.COMPANY_ADMIN) {
            // 管理员或者公司管理员，不限量
            return false;
        }
        if (monthly == null || monthly == 0) {
            throw new InsufficientCreditsException("AI功能已禁用, 请联系组长开通。");
        }
        if (monthly == -1) {
            // 不限量
            return false;
        }
        int rows = systemUserRepository.freezeCredits(userId, estimated);
        if (rows == 0) {
            int available = monthly - user.getUsedAiCredits() - user.getFrozenAiCredits();
            throw new InsufficientCreditsException(
                    "AI额度不足，剩余 " + Math.max(available, 0) + " credits");
        }
        return true;
    }

    /**
     * 检查用户是否有可用积分（monthly - used - frozen > 0）。
     * 管理员、公司管理员、无限额用户（-1）直接返回 true。
     */
    public boolean hasAvailableCredits(Long userId) {
        SystemUser user = systemUserRepository.findById(userId).orElseThrow();
        if (user.getUserType() == SystemUserType.ADMIN || user.getUserType() == SystemUserType.COMPANY_ADMIN) {
            return true;
        }
        Integer monthly = user.getMonthlyAiCredits();
        if (monthly == null || monthly == 0) {
            return false;
        }
        if (monthly == -1) {
            return true;
        }
        return (monthly - user.getUsedAiCredits() - user.getFrozenAiCredits()) > 0;
    }

    /**
     * 宽松冻结：只要 available > 0 即允许冻结（允许超额），不抛异常。
     * 管理员、公司管理员、禁用(null/0)、无限额(-1)用户直接 return 不冻结。
     */
    @Transactional
    public void tryFreeze(Long userId, int estimated) {
        if (shouldSkipCreditsOperation(userId)) {
            return;
        }
        systemUserRepository.freezeCreditsIfPositive(userId, estimated);
    }

    /**
     * 与 tryFreeze 严格对称的 short-circuit：保证 freeze ↔ settle 一对一。
     * 否则 ADMIN/无限额用户的 SystemUser.frozenAiCredits/usedAiCredits 字段会被污染。
     */
    @Transactional
    public void settle(Long userId, int freezeAmount, int actualAmount) {
        if (shouldSkipCreditsOperation(userId)) {
            return;
        }
        systemUserRepository.settleCredits(userId, freezeAmount, actualAmount);
    }

    /**
     * 与 tryFreeze 严格对称的 short-circuit：保证 freeze ↔ unfreeze 一对一。
     */
    @Transactional
    public void unfreeze(Long userId, int freezeAmount) {
        if (shouldSkipCreditsOperation(userId)) {
            return;
        }
        systemUserRepository.unfreezeCredits(userId, freezeAmount);
    }

    /**
     * 与 tryFreeze 入口判断完全一致：ADMIN/COMPANY_ADMIN 或 monthlyAiCredits == null/0/-1 时跳过。
     * 用于让 settle/unfreeze 与 tryFreeze 严格对称。
     */
    private boolean shouldSkipCreditsOperation(Long userId) {
        SystemUser user = systemUserRepository.findById(userId).orElseThrow();
        if (user.getUserType() == SystemUserType.ADMIN || user.getUserType() == SystemUserType.COMPANY_ADMIN) {
            return true;
        }
        Integer monthly = user.getMonthlyAiCredits();
        return monthly == null || monthly == 0 || monthly == -1;
    }
}
