package cn.v7soft.admin.utils;

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
        boolean isComplex = searchType == SearchType.COMPLEX;
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
                .addConstraint(!belongDepartmentIds.isEmpty(), InAttribute.<Long>builder().name("contextInfo.departmentId").value(belongDepartmentIds).build());

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
                case PHONE -> value = order.getDeliveryInfo().getPhone();
                case DEVICE -> value = order.getRiskInfo().getDeviceId();
                case NAME -> {
                    value = order.getDeliveryInfo().getFirstName();
                    lastName = order.getDeliveryInfo().getLastName();
                }
            }
            String phone = value;
            if (StrUtil.isBlank(phone)) {
                phone = "";
            } else {
                phone = phone.trim();
                phone = phone.length() > 8 ? phone.substring(phone.length() - 8) : phone;
            }
            return orderQueryPageRequest
                    .addConstraint(RepeatType.IP == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("riskInfo.remoteIp").value(value).build())
                    .addConstraint(RepeatType.PHONE == request.getRepeatType(),
                                   EqualsQueryAttribute.builder().name("deliveryInfo.phoneLast8").value(phone).build())
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
        // 当搜索涉及 itemInfos 时，需要添加 distinct 避免因 JOIN 产生重复记录
        boolean needsDistinct = isComplex || searchType == SearchType.PRODUCT_TITLE || searchType == SearchType.MERCHANDISE;
        return orderQueryPageRequest
                .addConstraint(needsDistinct, new QueryAttribute() {
                    @Override
                    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                        query.distinct(true);
                        return criteriaBuilder.conjunction(); // 返回一个始终为 true 的谓词
                    }
                })
                .or()
                .addConstraint(!keywords.isEmpty() && (isComplex || searchType == SearchType.ORDER_ID), InAttribute.<Long>builder().name("id").value(keywords.stream().filter(ConvertUtils::isLong).map(Long::parseLong).toList()).build())
                .addConstraint(!keywords.isEmpty() && (isComplex || searchType == SearchType.ORDER_ID), InAttribute.<String>builder().name("originOrderId").value(keywords).build())
                .addConstraint(StrUtil.isNotBlank(phone) && (isComplex || searchType == SearchType.TELEPHONE), LikeAttribute.builder().name("deliveryInfo.phoneLast8").value(phone).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && (isComplex || searchType == SearchType.NAME), LikeAttribute.builder().name("deliveryInfo.firstName").value(keyword).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && (isComplex || searchType == SearchType.PRODUCT_TITLE), LikeAttribute.builder().name("itemInfos.title").value(keyword).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && (isComplex || searchType == SearchType.MERCHANDISE), LikeAttribute.builder().name("itemInfos.merchandise").value(keyword).build())
                .addConstraint(!keywords.isEmpty() && (isComplex || searchType == SearchType.REMOTE_IP), InAttribute.<String>builder().name("riskInfo.remoteIp").value(keywords).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && (isComplex || searchType == SearchType.ADDRESS), LikeAttribute.builder().name("deliveryInfo.address").value(keyword).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && isComplex, LikeAttribute.builder().name("contextInfo.websiteUrl").value(keyword).build())
                .addConstraint(StrUtil.isNotBlank(keyword) && searchType == SearchType.DOMAIN, LikeAttribute.builder().name("contextInfo.websiteUrl").value(keyword).build())
                .next()
                .addConstraint(RepeatType.IP == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.remoteIpRepeatCount").value(1).build())
                .addConstraint(RepeatType.PHONE == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.phoneRepeatCount").value(1).build())
                .addConstraint(RepeatType.NAME == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.nameRepeatCount").value(1).build())
                .addConstraint(RepeatType.DEVICE == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.deviceRepeatCount").value(1).build())
                .addConstraint(RepeatType.REAL_IP == request.getRepeatType(), GreaterThanAttribute.<Integer>builder().name("botOrderCheckInfo.realIpRepeatCount").value(1).build())
                ;
    }
}
