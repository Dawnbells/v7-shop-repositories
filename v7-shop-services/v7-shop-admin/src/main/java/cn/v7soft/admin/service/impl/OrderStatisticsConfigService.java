package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.SaveOrderStatisticsConfigRequest;
import cn.v7soft.admin.service.IOrderStatisticsConfigService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
import cn.v7soft.dao.repositories.primary.OrderStatisticsUserConfigRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OrderStatisticsConfigService implements IOrderStatisticsConfigService {

    static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";
    static final String USD = "USD";
    private static final BigDecimal MAX_RATE = new BigDecimal("1000000000");
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Za-z]{3}$");

    private final OrderStatisticsUserConfigRepository repository;
    private final CurrencyRepository currencyRepository;

    public OrderStatisticsConfigService(
            OrderStatisticsUserConfigRepository repository,
            CurrencyRepository currencyRepository
    ) {
        this.repository = repository;
        this.currencyRepository = currencyRepository;
    }

    @Transactional
    @Override
    public OrderStatisticsUserConfig getOrCreate(String browserTimeZoneId) {
        SystemUserDto loginUser = currentUser();
        OrderStatisticsUserConfig config = repository
                .findByCompanyIdAndOwnerId(loginUser.getCompanyId(), loginUser.getLongId())
                .orElseGet(() -> repository.save(OrderStatisticsUserConfig.builder()
                        .companyId(loginUser.getCompanyId())
                        .owner(loginUser.toOwner())
                        .defaultTargetCurrencyCode(USD)
                        .timeZoneId(validTimeZoneOrDefault(browserTimeZoneId))
                        .exchangeRates(defaultRates())
                        .build()));
        // 默认目标币种已停用/不存在时回退 USD（§8.4），避免后续以停用币查询报错
        if (!isSelectableTargetCurrency(config.getDefaultTargetCurrencyCode())) {
            config.setDefaultTargetCurrencyCode(USD);
            config = repository.save(config);
        }
        log.info("[统计调试] getOrCreate(browserTz={}) 用户={}:{} -> configId={} 时区={} 目标币种={}",
                browserTimeZoneId, loginUser.getCompanyId(), loginUser.getLongId(),
                config.getId(), config.getTimeZoneId(), config.getDefaultTargetCurrencyCode());
        return config;
    }

    @Transactional
    @Override
    public OrderStatisticsUserConfig save(SaveOrderStatisticsConfigRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request, "配置不能为空");
        SystemUserDto loginUser = currentUser();
        String targetCurrencyCode = normalizeCurrencyCode(request.getDefaultTargetCurrencyCode());
        // 目标币种只能是 USD 或公司当前有效币种（§8.4）
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(
                isSelectableTargetCurrency(targetCurrencyCode),
                "目标币种必须是公司当前有效币种"
        );
        String timeZoneId = validateTimeZone(request.getTimeZoneId());
        Map<String, String> exchangeRates = normalizeRates(request.getExchangeRates());

        OrderStatisticsUserConfig config = repository
                .findByCompanyIdAndOwnerId(loginUser.getCompanyId(), loginUser.getLongId())
                .orElseGet(() -> OrderStatisticsUserConfig.builder()
                        .companyId(loginUser.getCompanyId())
                        .owner(loginUser.toOwner())
                        .build());

        config.setCompanyId(loginUser.getCompanyId());
        config.setOwner(loginUser.toOwner());
        config.setDefaultTargetCurrencyCode(targetCurrencyCode);
        config.setTimeZoneId(timeZoneId);
        config.setExchangeRates(exchangeRates);
        OrderStatisticsUserConfig saved = repository.save(config);
        log.info("[统计调试] save 用户={}:{} -> configId={} 保存时区={} 目标币种={}",
                loginUser.getCompanyId(), loginUser.getLongId(),
                saved.getId(), saved.getTimeZoneId(), saved.getDefaultTargetCurrencyCode());
        return saved;
    }

    private Map<String, String> normalizeRates(Map<String, String> rawRates) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(rawRates, "个人汇率不能为空");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put(USD, "1");
        for (Map.Entry<String, String> entry : rawRates.entrySet()) {
            String code = normalizeCurrencyCode(entry.getKey());
            if (USD.equals(code)) {
                continue;
            }
            result.put(code, normalizeRate(entry.getValue()));
        }
        return result;
    }

    private String normalizeRate(String rawRate) {
        try {
            ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(rawRate, "汇率不能为空");
            BigDecimal rate = new BigDecimal(rawRate.trim());
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(rate.compareTo(BigDecimal.ZERO) > 0, "汇率必须大于0");
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(rate.compareTo(MAX_RATE) <= 0, "汇率超过允许的最大值");
            BigDecimal normalized = rate.stripTrailingZeros();
            int scale = Math.max(0, normalized.scale());
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(scale <= 8, "汇率最多保留8位小数");
            return normalized.toPlainString();
        } catch (NumberFormatException exception) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("汇率格式不正确");
        }
    }

    private String normalizeCurrencyCode(String rawCode) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(rawCode, "币种代码不能为空");
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(
                CURRENCY_CODE_PATTERN.matcher(code).matches(),
                "币种代码必须是3位字母"
        );
        return code;
    }

    private String validateTimeZone(String rawTimeZoneId) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(rawTimeZoneId, "时区不能为空");
        try {
            return ZoneId.of(rawTimeZoneId.trim()).getId();
        } catch (DateTimeException exception) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("时区格式不正确");
        }
    }

    private String validTimeZoneOrDefault(String rawTimeZoneId) {
        if (StrUtil.isBlank(rawTimeZoneId)) {
            return DEFAULT_TIME_ZONE;
        }
        try {
            return ZoneId.of(rawTimeZoneId.trim()).getId();
        } catch (DateTimeException exception) {
            return DEFAULT_TIME_ZONE;
        }
    }

    private Map<String, String> defaultRates() {
        LinkedHashMap<String, String> rates = new LinkedHashMap<>();
        rates.put(USD, "1");
        return rates;
    }

    /**
     * 目标币种是否可选：USD 始终可选，其余必须是公司当前有效币种（§8.4）。code 为空视为不可选。
     */
    private boolean isSelectableTargetCurrency(String code) {
        if (code == null) {
            return false;
        }
        if (USD.equalsIgnoreCase(code)) {
            return true;
        }
        return currencyRepository.findAllValid().stream()
                .anyMatch(currency -> code.equalsIgnoreCase(currency.getCode()));
    }

    private SystemUserDto currentUser() {
        return SaSessionUtil.getLoginUser();
    }
}
