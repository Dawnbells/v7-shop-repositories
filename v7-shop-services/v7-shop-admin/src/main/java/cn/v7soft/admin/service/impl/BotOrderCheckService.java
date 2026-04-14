package cn.v7soft.admin.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.http.util.TextUtils;
import org.springframework.stereotype.Service;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.service.IAliyunOssService;
import cn.v7soft.admin.service.IBotOrderCheckService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IEmailService;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.dto.OrderEmailDto;
import cn.v7soft.admin.service.dto.TemporaryOrderDto;
import cn.v7soft.admin.service.dto.TextModerationData;
import cn.v7soft.admin.service.remote.IIpApiService;
import cn.v7soft.common.utils.RegexPattern;
import cn.v7soft.dao.entities.address.RemoteArea;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.IpDetailInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderBotCheckInfo;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.entities.primary.OrderRiskRecordInfo;
import cn.v7soft.dao.entities.primary.ProxyDetectInfo;
import cn.v7soft.dao.enums.CheckStatus;
import cn.v7soft.dao.repositories.address.RemoteAreaRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.ProxyDetectInfoRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotOrderCheckService implements IBotOrderCheckService {

    private final IIpApiService ipApiService;
    private final ICompanyService companyService;
    private final IOrderService orderService;
    private final IAliyunOssService aliyunOssService;
    private final IEmailService emailService;
    private final OrderRepository orderRepository;
    private final RemoteAreaRepository remoteAreaRepository;
    private final ProxyDetectInfoRepository proxyDetectInfoRepository;

    @Override
    public void botReviewOrder(TemporaryOrderDto temporaryOrderDto) {
        Long companyId = temporaryOrderDto.getCompanyId();
        Company company = companyService.companyCached(companyId);
        TenantContext.setCurrentTenant(companyId, company);
        TenantContext.restore();
        this.botReviewOrder(company, temporaryOrderDto);
    }

    private void botReviewOrder(Company company, TemporaryOrderDto temporaryOrderDto) {
        Order orderInfo = temporaryOrderDto.toOrderInfo();
        log.debug("temporary = {}, order = {}", JSONUtil.toJsonPrettyStr(temporaryOrderDto), JSONUtil.toJsonPrettyStr(orderInfo));
        OrderContextInfo contextInfo = orderInfo.getContextInfo();
        OrderDeliveryInfo deliveryInfo = orderInfo.getDeliveryInfo();
        OrderRiskRecordInfo riskInfo = orderInfo.getRiskInfo();
        String phone = deliveryInfo.getPhone();

        // 【偏远地区】
        Optional<RemoteArea> remoteArea = remoteAreaRepository.isRemoteArea(contextInfo.getCountryCode(), deliveryInfo.getPostalCode());
        boolean isRemoteArea = remoteArea.isPresent();
        String remoteTip = remoteArea.isPresent() ? remoteArea.get().getTip() : "";
        // 【测试单】
        boolean isTestOrder = StrUtil.isNotBlank(orderInfo.getFromUrl()) && orderInfo.getFromUrl().contains("ticket=");
        //【电话号码有误】;
        boolean isInvalidPhone = !ReUtil.isMatch(contextInfo.getPhoneRule(), phone);
        //【地址不全-纯文字】
        boolean isIncompletePlainTextAddress = ReUtil.isMatch(RegexPattern.REGEX_PLAIN_TEXT, deliveryInfo.getAddress());
        //【地址不全-纯数字】
        boolean isIncompletePureNumbersAddress = ReUtil.isMatch(RegexPattern.REGEX_PURE_NUMBERS, deliveryInfo.getAddress());
        //【邮箱缺失】
        boolean isEmailMissing = TextUtils.isBlank(deliveryInfo.getEmail());
        //【邮箱有误】
        boolean isInvalidEmail = !isEmailMissing && !ReUtil.isMatch(RegexPattern.REGEX_EMAIL, deliveryInfo.getEmail());
        // 【产品数量＞2】
        boolean isMoreThanTwoProducts = orderInfo.getItemCount() >= 2;
        // 【IP不一致】
        boolean isIpConflict = false;
        String conflictIp = "";

        Optional<ProxyDetectInfo> proxyDetectInfoOptional = proxyDetectInfoRepository.findByPdValAndPdKey(riskInfo.getPdKey(), riskInfo.getPdVal());
        if (proxyDetectInfoOptional.isPresent()) {
            // 经过屏蔽系统的订单，已经存在IP信息
            ProxyDetectInfo proxyDetectInfo = proxyDetectInfoOptional.get();
            if (proxyDetectInfo.getRemoteIp() == null || ObjectUtil.notEqual(proxyDetectInfo.getRemoteIp().getIp(), riskInfo.getRemoteIp())) {
                isIpConflict = true;
                conflictIp = proxyDetectInfo.getRemoteIp() == null ? "" : proxyDetectInfo.getRemoteIp().getIp();
                IpDetailInfo remoteIpInfo = ipApiService.requestIpDataInfo(riskInfo.getRemoteIp());
                riskInfo.setRemoteIpInfo(remoteIpInfo == null ? "" : JSONUtil.toJsonStr(remoteIpInfo));
            } else {
                riskInfo.setRemoteIpInfo(JSONUtil.toJsonStr(proxyDetectInfo.getRemoteIp()));
            }

            if (proxyDetectInfo.getRealIp() != null) {
                // 真实IP只从屏蔽系统回调中获取
                riskInfo.setRealIp(proxyDetectInfo.getRealIp().getIp());
                riskInfo.setRealIpInfo(JSONUtil.toJsonStr(proxyDetectInfo.getRealIp()));
            }
        } else if (StrUtil.isNotBlank(riskInfo.getRemoteIp())) {
            IpDetailInfo remoteIpInfo = ipApiService.requestIpDataInfo(riskInfo.getRemoteIp());
            riskInfo.setRemoteIpInfo(remoteIpInfo == null ? "" : JSONUtil.toJsonStr(remoteIpInfo));
        } else {
            riskInfo.setRemoteIpInfo("");
        }

        // 检查备注是否客诉
        String remark = deliveryInfo.getRemark();
        LocalDateTime june2025 = LocalDateTimeUtil.parse("2025-09-01T00:00:00");
        JSONObject remarkRiskDetail = new JSONObject();
        List<String> labels = new ArrayList<>();
        if (orderInfo.getCreateTime().isAfter(june2025)) {
            TextModerationData textModerationData = aliyunOssService.detectText(String.valueOf(company.getId()), remark);
            // 标签
            labels = textModerationData.getTranslatedLabels();
            String riskLevel = textModerationData.riskLevel();
            List<String> riskWords = textModerationData.riskWords().stream().map(String::toLowerCase).distinct().sorted(Comparator.reverseOrder()).toList();
            remarkRiskDetail.set("labels", labels);
            remarkRiskDetail.set("riskLevel", riskLevel);
            remarkRiskDetail.set("riskWords", riskWords);
        } else {
            remarkRiskDetail.set("labels", new ArrayList<>());
            remarkRiskDetail.set("riskLevel", "");
            remarkRiskDetail.set("riskWords", "");
        }

        // 重单计算只在同一国家范围内进行
        String countryCode = contextInfo.getCountryCode();
        // 终端查重
        int deviceRepeatCount = StrUtil.isBlank(riskInfo.getDeviceId()) ? 1 : orderRepository.findEarlierOrdersByDeviceId(riskInfo.getDeviceId(), countryCode) + 1;
        // 名字查重
        int nameRepeatCount = orderRepository.findEarlierOrdersByName(deliveryInfo.getFirstName(), deliveryInfo.getLastName(), countryCode) + 1;
        // 电话号码查重
        int phoneRepeatCount = orderRepository.findEarlierOrdersByPhoneLast8(orderInfo.getDeliveryInfo().getPhoneLast8(), countryCode) + 1;
        // 远程IP查重
        int remoteIpRepeatCount = orderRepository.findEarlierOrdersByRemoteIp(riskInfo.getRemoteIp(), countryCode) + 1;
        // 真实IP查重
        int realIpRepeatCount = StrUtil.isBlank(riskInfo.getRealIp()) ? 1 : orderRepository.findEarlierOrdersByRealIp(riskInfo.getRealIp(), countryCode) + 1;

        // 计算距离上次同手机和IP下单时间间距
        LocalDateTime orderTime = orderInfo.getOrderTime();
        Optional<Order> lastEarlierOrdersByRemoteIp = orderRepository.findLastEarlierOrdersByRemoteIp(orderInfo.getRiskInfo().getRemoteIp(), orderTime, countryCode);
        Optional<Order> lastEarlierOrdersByPhone = orderRepository.findLastEarlierOrdersByPhone(orderInfo.getDeliveryInfo().getPhoneLast8(), orderTime, countryCode);
        long phoneSecondsBetween = -1;
        long ipSecondsBetween = -1;
        if (lastEarlierOrdersByRemoteIp.isPresent()) {
            LocalDateTime lastOrderTimeByIp = lastEarlierOrdersByRemoteIp.get().getOrderTime();
            ipSecondsBetween = Math.abs(LocalDateTimeUtil.between(lastOrderTimeByIp, orderTime).toSeconds());
        }
        if (lastEarlierOrdersByPhone.isPresent()) {
            LocalDateTime lastOrderTimeByPhone = lastEarlierOrdersByPhone.get().getOrderTime();
            phoneSecondsBetween = Math.abs(LocalDateTimeUtil.between(lastOrderTimeByPhone, orderTime).toSeconds());
        }

        boolean hasWaring = isRemoteArea
                            || isTestOrder
                            || isInvalidPhone
                            || isIncompletePlainTextAddress
                            || isIncompletePureNumbersAddress
                            || isEmailMissing
                            || isInvalidEmail
                            || isMoreThanTwoProducts
                            || isIpConflict
                            || !labels.isEmpty();
        boolean hasDuplication = deviceRepeatCount > 1
                                 || nameRepeatCount > 1
                                 || phoneRepeatCount > 1
                                 || remoteIpRepeatCount > 1
                                 || realIpRepeatCount > 1;

        OrderBotCheckInfo orderBotCheckInfo = OrderBotCheckInfo.builder()
                .isRemoteArea(isRemoteArea)
                .remoteTip(remoteTip)
                .isTestOrder(isTestOrder)
                .isCloakOrder(Boolean.TRUE.equals(riskInfo.getCloak()))
                .isInvalidPhone(isInvalidPhone)
                .isIncompletePlainTextAddress(isIncompletePlainTextAddress)
                .isIncompletePureNumbersAddress(isIncompletePureNumbersAddress)
                .isEmailMissing(isEmailMissing)
                .isInvalidEmail(isInvalidEmail)
                .isMoreThanTwoProducts(isMoreThanTwoProducts)
                .isIpConflict(isIpConflict)
                .conflictIp(conflictIp)
                .deviceRepeatCount(deviceRepeatCount)
                .nameRepeatCount(nameRepeatCount)
                .phoneRepeatCount(phoneRepeatCount)
                .remoteIpRepeatCount(remoteIpRepeatCount)
                .realIpRepeatCount(realIpRepeatCount)
                .hasRemarkRisk(!labels.isEmpty())
                .remarkRiskDetail(labels.isEmpty() ? null : JSONUtil.toJsonPrettyStr(remarkRiskDetail))
                .phoneSecondsBetween(phoneSecondsBetween)
                .ipSecondsBetween(ipSecondsBetween)
                .build();

        CheckStatus botOrderStatus = CheckStatus.NORMAL;
        if (hasWaring && hasDuplication) {
            botOrderStatus = CheckStatus.DUPLICATE_WARNING;
        } else if (hasWaring) {
            botOrderStatus = CheckStatus.WARNING;
        } else if (hasDuplication) {
            botOrderStatus = CheckStatus.DUPLICATE;
        }
        orderInfo.setBotOrderCheckInfo(orderBotCheckInfo);
        orderInfo.setBotOrderStatus(botOrderStatus);
        orderInfo.getItemInfos().forEach(orderItemInfo -> orderItemInfo.setOrder(orderInfo));
        // 按 skuCode 分组合并数量，避免重复
        orderInfo.setSkuCodes(orderInfo.getItemInfos().stream()
                .collect(Collectors.groupingBy(OrderItemInfo::getSkuCode, Collectors.summingLong(OrderItemInfo::getQuantity)))
                .entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getKey() + "x" + e.getValue() : e.getKey())
                .collect(Collectors.joining("+")));
        orderInfo.setSkuNames(orderInfo.getItemInfos().stream()
                .collect(Collectors.groupingBy(OrderItemInfo::getSkuName, Collectors.summingLong(OrderItemInfo::getQuantity)))
                .entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getKey() + "x" + e.getValue() : e.getKey())
                .collect(Collectors.joining("+")));
        orderInfo.setQuantity(orderInfo.getItemInfos().stream().mapToLong(OrderItemInfo::getQuantity).sum());
        Order savedOrder = orderService.saveAndFlush(orderInfo);

        // 在事务内提取邮件所需数据为 DTO，避免 @Async 线程中触发 LAZY 加载
        OrderEmailDto emailDto = OrderEmailDto.from(savedOrder);
        emailService.sendOrderConfirmationEmail(emailDto);
    }
}
