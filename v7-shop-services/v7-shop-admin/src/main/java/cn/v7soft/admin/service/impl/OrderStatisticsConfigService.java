package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.SaveOrderStatisticsConfigRequest;
import cn.v7soft.admin.service.IOrderStatisticsConfigService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.repositories.primary.OrderStatisticsUserConfigRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class OrderStatisticsConfigService implements IOrderStatisticsConfigService {

    static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";
    static final String USD = "USD";
    private static final BigDecimal MAX_RATE = new BigDecimal("1000000000");
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Za-z]{3}$");

    private final OrderStatisticsUserConfigRepository repository;

    public OrderStatisticsConfigService(OrderStatisticsUserConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @Override
    public OrderStatisticsUserConfig getOrCreate(String browserTimeZoneId) {
        SystemUserDto loginUser = currentUser();
        return repository.findByCompanyIdAndOwnerId(loginUser.getCompanyId(), loginUser.getLongId())
                .orElseGet(() -> repository.save(OrderStatisticsUserConfig.builder()
                        .companyId(loginUser.getCompanyId())
                        .owner(loginUser.toOwner())
                        .defaultTargetCurrencyCode(USD)
                        .timeZoneId(validTimeZoneOrDefault(browserTimeZoneId))
                        .exchangeRates(defaultRates())
                        .build()));
    }

    @Transactional
    @Override
    public OrderStatisticsUserConfig save(SaveOrderStatisticsConfigRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request, "配置不能为空");
        SystemUserDto loginUser = currentUser();
        String targetCurrencyCode = normalizeCurrencyCode(request.getDefaultTargetCurrencyCode());
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
        return repository.save(config);
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

    private SystemUserDto currentUser() {
        return SaSessionUtil.getLoginUser();
    }
}
