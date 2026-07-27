package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.IEmailService;
import cn.v7soft.admin.service.dto.OrderEmailDto;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.admin.service.email.OrderEmailConfigurationResolver;
import cn.v7soft.admin.service.email.ResolvedOrderEmailConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;

@Primary
@Service
@Slf4j
@RequiredArgsConstructor
public class ResolvedOrderEmailService implements IEmailService {

    private final OrderEmailConfigurationResolver configurationResolver;
    private final EmailSmtpSupport smtpSupport;

    @Async
    @Override
    public void sendOrderConfirmationEmail(OrderEmailDto dto) {
        String recipient = dto.getEmail();
        if (StrUtil.isBlank(recipient)) {
            log.info("订单邮件已跳过: orderId={}, companyId={}, departmentId={}, reason=recipient-empty",
                    dto.getId(), dto.getCompanyId(), dto.getDepartmentId());
            return;
        }

        try {
            ResolvedOrderEmailConfig resolved = configurationResolver.resolve(
                    dto.getDepartmentId(), dto.getCompanyId(), dto.getLanguageCode());
            if (!resolved.enabled()) {
                log.info("订单邮件已跳过: orderId={}, companyId={}, departmentId={}, reason={}",
                        dto.getId(), dto.getCompanyId(), dto.getDepartmentId(), resolved.smtpSource());
                return;
            }

            JSONObject smtp = resolved.smtp();
            smtpSupport.sendHtml(
                    smtp,
                    recipient,
                    buildSubject(resolved.template(), dto),
                    buildContent(resolved.template(), dto));
            log.info("订单邮件发送成功: orderId={}, companyId={}, departmentId={}, recipient={}, provider={}, smtpSource={}, templateSource={}",
                    dto.getId(), dto.getCompanyId(), dto.getDepartmentId(), maskEmail(recipient),
                    smtpSupport.provider(smtp), resolved.smtpSource(), resolved.templateSource());
        } catch (Exception exception) {
            log.error("订单邮件发送失败: orderId={}, companyId={}, departmentId={}, recipient={}, errorType={}",
                    dto.getId(), dto.getCompanyId(), dto.getDepartmentId(), maskEmail(recipient),
                    exception.getClass().getSimpleName(), exception);
        }
    }

    private String buildSubject(JSONObject template, OrderEmailDto dto) {
        if (template != null && StrUtil.isNotBlank(template.getStr("subject"))) {
            return replaceVariables(template.getStr("subject"), dto);
        }
        return String.format("Order Confirmation - #%s", dto.getOriginOrderId());
    }

    private String buildContent(JSONObject template, OrderEmailDto dto) {
        if (template != null && StrUtil.isNotBlank(template.getStr("content"))) {
            return replaceVariables(template.getStr("content"), dto);
        }
        return String.format("""
                <p>Dear %s %s,</p>
                <p>Thank you for your order!</p>
                <p>Order Number: %s</p>
                <p>We will process your order shortly.</p>
                <p>Best regards,<br>Customer Service Team</p>
                """, dto.getFirstName(), dto.getLastName(), dto.getOriginOrderId());
    }

    private String replaceVariables(String template, OrderEmailDto dto) {
        String name = StrUtil.nullToEmpty(dto.getFirstName()) + " "
                + StrUtil.nullToEmpty(dto.getLastName());
        String region = StrUtil.nullToEmpty(dto.getDistrict()) + " "
                + StrUtil.nullToEmpty(dto.getCity()) + " "
                + StrUtil.nullToEmpty(dto.getProvince());

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
        if (dto.getCurrencyCode() != null) {
            currencyFormat.setCurrency(Currency.getInstance(dto.getCurrencyCode()));
        }
        BigDecimal totalAmount = dto.getTotalAmount() == null ? BigDecimal.ZERO : dto.getTotalAmount();
        StringBuilder items = new StringBuilder();
        if (dto.getItems() != null) {
            dto.getItems().forEach(item -> {
                BigDecimal price = item.getSellPrice() == null ? BigDecimal.ZERO : item.getSellPrice();
                items.append(item.getSpecTitle())
                        .append("<br/>")
                        .append(currencyFormat.format(price.doubleValue()))
                        .append(" × ")
                        .append(item.getQuantity())
                        .append("<br/>");
            });
        }

        return template
                .replace("{{customer_name}}", name.trim())
                .replace("{{customer_phone}}", StrUtil.nullToEmpty(dto.getPhone()))
                .replace("{{customer_email}}", StrUtil.nullToEmpty(dto.getEmail()))
                .replace("{{customer_address}}", StrUtil.nullToEmpty(dto.getAddress()))
                .replace("{{customer_region}}", region.trim())
                .replace("{{customer_postal_code}}", StrUtil.nullToEmpty(dto.getPostalCode()))
                .replace("{{customer_remark}}", StrUtil.nullToEmpty(dto.getRemark()))
                .replace("{{order_id}}", orderId(dto))
                .replace("{{order_amount}}", currencyFormat.format(totalAmount.doubleValue()))
                .replace("{{order_items}}", items.toString().trim());
    }

    private String orderId(OrderEmailDto dto) {
        if (dto.getOriginOrderId() != null) {
            return dto.getOriginOrderId();
        }
        return dto.getId() == null ? "" : dto.getId().toString();
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
