package cn.v7soft.admin.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.QueryOrderRequest;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.GreaterThanAttribute;
import cn.v7soft.core.controller.request.attributes.InAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.enums.RepeatType;
import cn.v7soft.dao.enums.SearchType;
import cn.v7soft.dao.tenant.WebsiteContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderQueryHelper {

    public static QueryPageRequest<Order> convertOrderQueryPageRequest(QueryOrderRequest request, IOrderService orderService) {
        SearchType searchType = request.getSearchType();
        String keyword = request.getKeywords() == null ? "" : request.getKeywords().trim();
        request.setSortBy("orderTime desc, id desc");

        List<Long> belongEmployeeIds = new ArrayList<>();
        if (request.getBelongEmployeeIds() != null && !request.getBelongEmployeeIds().isEmpty()) {
            for (String belongEmployeeId : request.getBelongEmployeeIds()) {
                if (ConvertUtils.isLong(belongEmployeeId)) {
                    belongEmployeeIds.add(ConvertUtils.parseLong(belongEmployeeId));
                }
            }
        }
        List<Long> belongDepartmentIds = new ArrayList<>();
        if (request.getBelongDepartmentIds() != null && !request.getBelongDepartmentIds().isEmpty()) {
            for (String belongDepartmentId : request.getBelongDepartmentIds()) {
                if (ConvertUtils.isLong(belongDepartmentId)) {
                    belongDepartmentIds.add(ConvertUtils.parseLong(belongDepartmentId));
                }
            }
        }

        QueryPageRequest<Order> orderQueryPageRequest = QueryPageRequest.<Order>fromRequest(request)
                .addConstraint(request.getDateRange() != null && request.getDateRange().size() == 2, new QueryAttribute() {
                    @Override
                    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                        Path<LocalDateTime> createTime = root.get("orderTime");
                        return criteriaBuilder.between(createTime, request.getDateRange().get(0), request.getDateRange().get(1));
                    }
                })
                .addConstraint(ObjectUtil.isNotNull(request.getPlatform()), EqualsQueryAttribute.builder().name("platform").value(request.getPlatform()).build())
                .addConstraint(ObjectUtil.isNotNull(request.getOrderStatus()), EqualsQueryAttribute.builder().name("orderStatus").value(request.getOrderStatus()).build())
                .addConstraint(ObjectUtil.isNotNull(request.getBotOrderStatus()), EqualsQueryAttribute.builder().name("botOrderStatus").value(request.getBotOrderStatus()).build())
                .addConstraint(ConvertUtils.isLong(request.getCountryId()), EqualsQueryAttribute.builder().name("contextInfo.countryId").value(request.getCountryId()).build())
                .addConstraint(WebsiteContext.isWebsiteAdmin(), EqualsQueryAttribute.builder()
                        .name("contextInfo.websiteId")
                        .value(WebsiteContext.getCurrentWebsiteId())
                        .build())
                .addConstraint(!belongEmployeeIds.isEmpty(), InAttribute.<Long>builder().name("contextInfo.salesUid").value(belongEmployeeIds).build())
                .addConstraint(!belongDepartmentIds.isEmpty(), InAttribute.<Long>builder().name("contextInfo.departmentId").value(belongDepartmentIds).build())
                .addConstraint(ObjectUtil.isNotNull(request.getContacted()), EqualsQueryAttribute.builder().name("contacted").value(request.getContacted()).build());

        if (SearchType.REPEAT == searchType) {
            if (!ConvertUtils.isLong(keyword) || orderService == null) {
                return QueryPageRequest.<Order>fromRequest(request).add(EqualsQueryAttribute.builder().name("id").value("1").build());
            }
            Optional<Order> orderOptional = orderService.findById(ConvertUtils.parseLong(keyword));
            if (orderOptional.isEmpty()) {
                return QueryPageRequest.<Order>fromRequest(request).add(EqualsQueryAttribute.builder().name("id").value("1").build());
            }
            Order order = orderOptional.get();
            String value = "";
            String lastName = "";
            switch (request.getRepeatType()) {
                case REAL_IP -> value = order.getRiskInfo().getRealIp();
                case IP -> value = order.getRiskInfo().getRemoteIp();
                case PHONE -> value = order.getDeliveryInfo().getPhoneLast8();
                case DEVICE -> value = order.getRiskInfo().getDeviceId();
                case NAME -> {
                    value = order.getDeliveryInfo().getFirstName();
                    lastName = order.getDeliveryInfo().getLastName();
                }
            }
            return orderQueryPageRequest
                    .addConstraint(RepeatType.IP == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("riskInfo.remoteIp").value(value).build())
                    .addConstraint(RepeatType.PHONE == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("deliveryInfo.phoneLast8").value(value).build())
                    .addConstraint(RepeatType.NAME == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("deliveryInfo.firstName").value(value).build())
                    .addConstraint(RepeatType.NAME == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("deliveryInfo.lastName").value(lastName).build())
                    .addConstraint(RepeatType.DEVICE == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("riskInfo.deviceId").value(value).build())
                    .addConstraint(RepeatType.REAL_IP == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("riskInfo.realIp").value(value).build());

        }
        String delimiter = " ";
        if (request.getKeywords() == null) {
            delimiter = " ";
        } else if (request.getKeywords().contains(",")) {
            delimiter = ",";
        } else if (request.getKeywords().contains("\n")) {
            delimiter = "\n";
        }

        List<String> keywords = request.getKeywords() == null ? Collections.emptyList() : Arrays.stream(request.getKeywords().split(delimiter)).filter(StrUtil::isNotBlank).map(StrUtil::trim).toList();
        String phone = request.getKeywords();
        if (StrUtil.isBlank(phone)) {
            phone = "";
        } else {
            phone = phone.trim();
            phone = phone.length() > 8 ? phone.substring(phone.length() - 8) : phone;
        }
        String domainKeyword = normalizeDomainKeyword(keyword);
        boolean needsDistinct = (searchType == SearchType.PRODUCT_TITLE || searchType == SearchType.MERCHANDISE)
                                && !keyword.isEmpty();
        boolean strictDomainMatch = domainKeyword.chars().filter(c -> c == '.').count() > 1;
        // searchType 由前端按 keyword 形态推导确定，后端按精确分支匹配（不再做"综合 OR 多字段"）
        // 同一 searchType 下若涉及多字段（如 ORDER_ID 同时查 id 和 originOrderId），仍用 .or() 段组合
        return orderQueryPageRequest
                .addConstraint(needsDistinct, new QueryAttribute() {
                    @Override
                    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                        query.distinct(true);
                        return criteriaBuilder.conjunction();
                    }
                })
                .or()
                .addConstraint(!keywords.isEmpty() && searchType == SearchType.ORDER_ID, InAttribute.<Long>builder().name("id").value(keywords.stream().filter(ConvertUtils::isLong).map(Long::parseLong).toList()).build())
                .addConstraint(!keywords.isEmpty() && searchType == SearchType.ORDER_ID, InAttribute.<String>builder().name("originOrderId").value(keywords).build())
                .addConstraint(StrUtil.isNotBlank(phone) && searchType == SearchType.TELEPHONE, LikeAttribute.builder().name("deliveryInfo.phoneLast8").value(phone).leftMatch(false).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && searchType == SearchType.NAME, LikeAttribute.builder().name("deliveryInfo.firstName").value(keyword).leftMatch(false).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && searchType == SearchType.PRODUCT_TITLE, new FullTextMatchAttribute("itemInfos.title", keyword))
                .addConstraint(StrUtil.isNotBlank(keyword) && searchType == SearchType.MERCHANDISE, new FullTextMatchAttribute("itemInfos.merchandise", keyword))
                .addConstraint(!keywords.isEmpty() && searchType == SearchType.REMOTE_IP, InAttribute.<String>builder().name("riskInfo.remoteIp").value(keywords).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && searchType == SearchType.ADDRESS, new FullTextMatchAttribute("deliveryInfo.address", keyword))
                .addConstraint(StrUtil.isNotBlank(domainKeyword) && searchType == SearchType.DOMAIN,
                               strictDomainMatch ? EqualsQueryAttribute.builder().name("contextInfo.websiteUrl").value(domainKeyword.trim()).build()
                                                 : LikeAttribute.builder().name("contextInfo.websiteUrl").value(domainKeyword.trim()).leftMatch(true).build())
                .next()
                .addConstraint(RepeatType.IP == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.remoteIpRepeatCount").value(1).build())
                .addConstraint(RepeatType.PHONE == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.phoneRepeatCount").value(1).build())
                .addConstraint(RepeatType.NAME == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.nameRepeatCount").value(1).build())
                .addConstraint(RepeatType.DEVICE == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.deviceRepeatCount").value(1).build())
                .addConstraint(RepeatType.REAL_IP == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.realIpRepeatCount").value(1).build())
                ;
    }

    /**
     * 按 "." 边界严格匹配 host 的查询条件。
     * <p>
     * websiteUrl 字段存储的是纯 host（如 "de.varlar.com"），由 v7-shop-mall 写入：
     * websiteUrl = pageContext.subDomain.fullName
     * <p>
     * 匹配语义：
     * <ul>
     *   <li>一级域名（点数 ≤ 1，例如 "varlar.com"）：等于自身 OR 以 ".${keyword}" 结尾，
     *       匹配 varlar.com、de.varlar.com、ide.varlar.com 等全部子域名；不会匹配 notvarlar.com</li>
     *   <li>子域名（点数 ≥ 2，例如 "de.varlar.com"）：仅精确等于自身，不再做后缀模糊，
     *       严格区分 de.varlar.com 与 ide.varlar.com 等同后缀 host</li>
     * </ul>
     */
    static QueryAttribute buildHostMatchAttribute(String hostKeyword) {
        long dotCount = hostKeyword.chars().filter(c -> c == '.').count();

        return new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                Path<String> path = root.<Object>get("contextInfo").<String>get("websiteUrl");
                long dotCount = hostKeyword.chars().filter(c -> c == '.').count();
                if (dotCount <= 1) {
                    return cb.or(
                            cb.equal(path, hostKeyword),
                            cb.like(path, "%." + hostKeyword)
                    );
                }
                return cb.equal(path, hostKeyword);
            }
        };
    }

    /**
     * 从任意 URL 格式中提取纯 host（不含协议、路径、查询串、fragment）。
     * 与 {@link #normalizeDomainKeyword} 的区别：不去 www 前缀，适用于写入端保留原始 host。
     */
    public static String extractHost(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed.contains("://") ? trimmed : "https://" + trimmed);
            String host = uri.getHost();
            if (StrUtil.isNotBlank(host)) {
                return host;
            }
        } catch (URISyntaxException ignored) {
        }
        String s = trimmed.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        int slashIdx = s.indexOf('/');
        if (slashIdx >= 0) s = s.substring(0, slashIdx);
        int queryIdx = s.indexOf('?');
        if (queryIdx >= 0) s = s.substring(0, queryIdx);
        int hashIdx = s.indexOf('#');
        if (hashIdx >= 0) s = s.substring(0, hashIdx);
        return s.trim();
    }

    static String normalizeDomainKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return "";
        }
        String trimmed = keyword.trim();
        try {
            URI uri = new URI(trimmed.contains("://") ? trimmed : "https://" + trimmed);
            String host = uri.getHost();
            if (StrUtil.isNotBlank(host)) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (URISyntaxException ignored) {
            log.debug("域名查询关键字不是标准URL: {}", trimmed);
        }
        String domain = trimmed
                .replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "")
                .replaceFirst("^www\\.", "");
        int slashIndex = domain.indexOf('/');
        if (slashIndex >= 0) {
            domain = domain.substring(0, slashIndex);
        }
        int queryIndex = domain.indexOf('?');
        if (queryIndex >= 0) {
            domain = domain.substring(0, queryIndex);
        }
        int hashIndex = domain.indexOf('#');
        if (hashIndex >= 0) {
            domain = domain.substring(0, hashIndex);
        }
        return domain.trim();
    }
}
