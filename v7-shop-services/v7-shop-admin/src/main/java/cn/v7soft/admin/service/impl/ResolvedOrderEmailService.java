package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.IEmailService;
import cn.v7soft.admin.service.dto.OrderEmailDto;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.admin.service.email.OrderEmailConfigurationResolver;
import cn.v7soft.admin.service.email.OrderEmailRenderer;
import cn.v7soft.admin.service.email.ResolvedOrderEmailConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Primary
@Service
@Slf4j
@RequiredArgsConstructor
public class ResolvedOrderEmailService implements IEmailService {

    private final OrderEmailConfigurationResolver configurationResolver;
    private final EmailSmtpSupport smtpSupport;
    private final OrderEmailRenderer orderEmailRenderer;

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
            OrderEmailRenderer.RenderedOrderEmail email = orderEmailRenderer.render(resolved.template(), dto);
            smtpSupport.sendHtml(
                    smtp,
                    recipient,
                    email.subject(),
                    email.content());
            log.info("订单邮件发送成功: orderId={}, companyId={}, departmentId={}, recipient={}, provider={}, smtpSource={}, templateSource={}",
                    dto.getId(), dto.getCompanyId(), dto.getDepartmentId(), maskEmail(recipient),
                    smtpSupport.provider(smtp), resolved.smtpSource(), resolved.templateSource());
        } catch (Exception exception) {
            log.error("订单邮件发送失败: orderId={}, companyId={}, departmentId={}, recipient={}, errorType={}",
                    dto.getId(), dto.getCompanyId(), dto.getDepartmentId(), maskEmail(recipient),
                    exception.getClass().getSimpleName(), exception);
        }
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
