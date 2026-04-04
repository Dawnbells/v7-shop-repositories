package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.exception.InsufficientCreditsException;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCreditsService {

    private final SystemUserRepository systemUserRepository;

    /**
     * 冻结指定 credits。
     * @return true=实际执行了冻结, false=用户无限额(monthlyAiCredits==-1)
     * @throws InsufficientCreditsException 额度不足或已禁用(null/0)
     */
    @Transactional
    public boolean freeze(Long userId, int estimated) {
        SystemUser user = systemUserRepository.findById(userId).orElseThrow();
        Integer monthly = user.getMonthlyAiCredits();
        if (monthly == null || monthly == 0) {
            throw new InsufficientCreditsException("AI功能已禁用, 请联系组长开通。");
        }
        if (monthly == -1) {
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

    @Transactional
    public void settle(Long userId, int freezeAmount, int actualAmount) {
        systemUserRepository.settleCredits(userId, freezeAmount, actualAmount);
    }

    @Transactional
    public void unfreeze(Long userId, int freezeAmount) {
        systemUserRepository.unfreezeCredits(userId, freezeAmount);
    }
}
