package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.exception.DailyQuotaExhaustedException;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.AiRateLimitMode;
import cn.v7soft.dao.repositories.primary.AiAccountRepository;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AiAccountService extends BaseDataRangeService<AiAccount, AiAccountRepository> implements IAiAccountService {

    private final AiTokenUsageRecordRepository aiTokenUsageRecordRepository;

    public AiAccountService(AiAccountRepository repository,
                            AiTokenUsageRecordRepository aiTokenUsageRecordRepository) {
        super(repository);
        this.aiTokenUsageRecordRepository = aiTokenUsageRecordRepository;
    }

    @Override
    public List<AiAccount> findAvailableAccounts(AiProvider provider) {
        return repository.findByProviderAndStatusOrderByPriorityAscIdAsc(provider, StatusEnum.VALID);
    }

    @Override
    public boolean hasDailyQuota(AiAccount account, int requestedCalls) {
        if (account == null) {
            return false;
        }
        if (account.getDailyLimit() == null) {
            return true;
        }
        if (requestedCalls <= 0) {
            return true;
        }
        long used = aiTokenUsageRecordRepository.countDailyCallsByAiAccount(
                account.getId(), LocalDate.now().atStartOfDay());
        return used + requestedCalls <= account.getDailyLimit();
    }

    @Override
    public void checkDailyQuota(AiAccount account, int requestedCalls) {
        if (!hasDailyQuota(account, requestedCalls)) {
            throw new DailyQuotaExhaustedException("AI账号今日配额已用尽");
        }
    }

    @Override
    protected void checkKeyConstraint(AiAccount entity) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(entity.getName(), "AI账号名称不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(entity.getApiKey(), "API Key不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(entity.getModel(), "模型不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(entity.getProvider(), "AI账号类型不能为空");

        checkPrice(entity.getTextInputPrice(), entity.getTextInputPriceUnit(), "文本输入");
        checkPrice(entity.getTextOutputPrice(), entity.getTextOutputPriceUnit(), "文本输出");
        checkPrice(entity.getImageInputPrice(), entity.getImageInputPriceUnit(), "图片输入");
        checkPrice(entity.getImageOutputPrice(), entity.getImageOutputPriceUnit(), "图片输出");
        checkPrice(entity.getVideoInputPrice(), entity.getVideoInputPriceUnit(), "视频输入");
        checkPrice(entity.getVideoOutputPrice(), entity.getVideoOutputPriceUnit(), "视频输出");
        if (entity.getDailyLimit() != null) {
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(entity.getDailyLimit() >= 0, "每日限额不能小于0");
        }
        AiRateLimitMode rateLimitMode = entity.getRateLimitMode() == null ? AiRateLimitMode.CONCURRENCY : entity.getRateLimitMode();
        if (rateLimitMode == AiRateLimitMode.RPD_RPM) {
            ClientResponseEnum.PARAMETER_ILLEGAL.notNull(entity.getRequestsPerDay(), "每日请求限制不能为空");
            ClientResponseEnum.PARAMETER_ILLEGAL.notNull(entity.getRequestsPerMinute(), "每分钟请求限制不能为空");
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(entity.getRequestsPerDay() > 0, "每日请求限制必须大于0");
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(entity.getRequestsPerMinute() > 0, "每分钟请求限制必须大于0");
        }
        if (rateLimitMode == AiRateLimitMode.CONCURRENCY) {
            ClientResponseEnum.PARAMETER_ILLEGAL.notNull(entity.getMaxConcurrency(), "最大并发数不能为空");
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(entity.getMaxConcurrency() > 0, "最大并发数必须大于0");
        }
        AiAccount existing = repository.findBySameName(entity.getName(), entity.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existing, "AI账号名称不允许重复");
    }

    private void checkPrice(BigDecimal price, Object priceUnit, String name) {
        if (price == null) {
            return;
        }
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(price.signum() >= 0, name + "价格不能小于0");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(priceUnit, name + "价格已填写时必须选择计费单位");
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.COMPANY);
    }
}
