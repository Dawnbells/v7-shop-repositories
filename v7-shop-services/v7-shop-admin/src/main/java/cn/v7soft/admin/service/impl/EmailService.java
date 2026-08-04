package cn.v7soft.admin.service.impl;

import java.util.Optional;
import java.util.Properties;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.admin.service.IEmailService;
import cn.v7soft.admin.service.dto.OrderEmailDto;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.admin.service.email.OrderEmailRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService implements IEmailService {

    private static final String EMAIL_CONFIG_NAME = "email";

    private final IDynamicConfigService dynamicConfigService;
    private final OrderEmailRenderer orderEmailRenderer;
    private final EmailSmtpSupport smtpSupport;

    @Async
    @Override
    public void sendOrderConfirmationEmail(OrderEmailDto dto) {
        try {
            String customerEmail = dto.getEmail();
            if (StrUtil.isBlank(customerEmail)) {
                log.warn("订单 {} 客户邮箱为空，跳过发送邮件", dto.getId());
                return;
            }

            Long departmentId = dto.getDepartmentId();
            Long companyId = dto.getCompanyId();

            Optional<JSONObject> configOpt = dynamicConfigService.getConfigWithFallback(
                    EMAIL_CONFIG_NAME, departmentId, companyId);

            if (configOpt.isEmpty()) {
                log.warn("订单 {} 未找到邮件配置，跳过发送邮件。部门ID: {}, 公司ID: {}",
                         dto.getId(), departmentId, companyId);
                return;
            }

            JSONObject config = configOpt.get();
            JSONObject emailConfig = config.getJSONObject("email");
            if (emailConfig == null) {
                log.warn("订单 {} 邮件配置中缺少 email 节点", dto.getId());
                return;
            }

            Boolean open = emailConfig.getBool("open", false);
            if (!Boolean.TRUE.equals(open)) {
                log.info("订单 {} 邮件功能未开启，跳过发送邮件", dto.getId());
                return;
            }

            JavaMailSenderImpl mailSender = createMailSender(emailConfig);
            if (mailSender == null) {
                log.error("订单 {} 创建邮件发送器失败", dto.getId());
                return;
            }

            String languageCode = dto.getLanguageCode();
            JSONObject emailTemplate = config.getJSONObject("email-template");
            JSONObject template = getTemplate(emailTemplate, languageCode);
            if (template == null) {
                template = getDefaultTemplate(emailTemplate);
            }

            OrderEmailRenderer.RenderedOrderEmail rendered = orderEmailRenderer.render(template, dto);

            smtpSupport.sendHtml(mailSender, smtpSupport.fromAddress(emailConfig), customerEmail,
                    rendered.subject(), rendered.content());
            log.info("订单 {} 确认邮件发送成功，收件人: {}, 语言: {}", dto.getId(), customerEmail, languageCode);

        } catch (Exception e) {
            log.error("订单 {} 发送邮件失败", dto.getId(), e);
        }
    }

    private JSONObject getDefaultTemplate(JSONObject emailTemplate) {
        return emailTemplate.keySet().stream()
                .filter(languageCode -> emailTemplate.getJSONObject(languageCode) != null)
                .map(emailTemplate::getJSONObject)
                .filter(languageTemplate -> languageTemplate.getBool("default", false))
                .findFirst().orElse(null);
    }

    private JavaMailSenderImpl createMailSender(JSONObject emailConfig) {
        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(emailConfig.getStr("host"));
            mailSender.setPort(emailConfig.getInt("port", 587));
            mailSender.setUsername(emailConfig.getStr("username"));
            mailSender.setPassword(emailConfig.getStr("password"));

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");

            Boolean secure = emailConfig.getBool("secure", false);
            if (Boolean.TRUE.equals(secure)) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.ssl.enable", "true");
            }

            props.put("mail.smtp.connectiontimeout", 5000);
            props.put("mail.smtp.timeout", 5000);
            props.put("mail.smtp.writetimeout", 5000);

            return mailSender;
        } catch (Exception e) {
            log.error("创建邮件发送器失败", e);
            return null;
        }
    }

    private JSONObject getTemplate(JSONObject emailTemplate, String languageCode) {
        if (emailTemplate == null || StrUtil.isBlank(languageCode)) {
            return null;
        }
        return emailTemplate.getJSONObject(languageCode);
    }

}

