package cn.v7soft.admin.service;

import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiProvider;

import java.util.List;

public interface IAiAccountService extends IBaseDataRangeService<AiAccount> {

    List<AiAccount> findAvailableAccounts(AiProvider provider);

    List<AiAccount> findAvailableAccountsByApiKey(AiProvider provider, String apiKey);

    boolean hasDailyQuota(AiAccount account, int requestedCalls);

    void checkDailyQuota(AiAccount account, int requestedCalls);
}
