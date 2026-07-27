package cn.v7soft.admin.service.email;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

@Component
public class EmailSmtpSupport {

    public static final String PROVIDER_SMTP = "SMTP";
    public static final String PROVIDER_AMAZON_SES = "AMAZON_SES";

    public JavaMailSenderImpl createMailSender(JSONObject emailConfig) {
        validateSmtp(emailConfig);
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(emailConfig.getStr("host"));
        mailSender.setPort(emailConfig.getInt("port", 587));
        mailSender.setUsername(emailConfig.getStr("username"));
        mailSender.setPassword(emailConfig.getStr("password"));

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (emailConfig.getBool("secure", false)) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", emailConfig.getInt("connection-timeout", 5000));
        props.put("mail.smtp.timeout", emailConfig.getInt("timeout", 5000));
        props.put("mail.smtp.writetimeout", emailConfig.getInt("write-timeout", 5000));
        return mailSender;
    }

    public void validate(JSONObject emailConfig) {
        String provider = provider(emailConfig);
        if (PROVIDER_SMTP.equals(provider)) {
            validateSmtp(emailConfig);
            return;
        }
        if (PROVIDER_AMAZON_SES.equals(provider)) {
            validateAmazonSes(emailConfig);
            return;
        }
        throw new IllegalArgumentException("不支持的邮件发送方式: " + provider);
    }

    public String provider(JSONObject emailConfig) {
        if (emailConfig == null) {
            throw new IllegalArgumentException("邮件发送配置不能为空");
        }
        String provider = emailConfig.getStr("provider");
        return StrUtil.isBlank(provider) ? PROVIDER_SMTP : provider.trim().toUpperCase(Locale.ROOT);
    }

    private void validateSmtp(JSONObject emailConfig) {
        if (emailConfig == null) {
            throw new IllegalArgumentException("SMTP 配置不能为空");
        }
        if (StrUtil.isBlank(emailConfig.getStr("host"))) {
            throw new IllegalArgumentException("SMTP 服务器地址不能为空");
        }
        Integer port = emailConfig.getInt("port");
        if (port == null || port <= 0 || port > 65535) {
            throw new IllegalArgumentException("SMTP 端口不正确");
        }
        if (StrUtil.isBlank(emailConfig.getStr("username"))) {
            throw new IllegalArgumentException("SMTP 用户名不能为空");
        }
        if (StrUtil.isBlank(emailConfig.getStr("password"))) {
            throw new IllegalArgumentException("SMTP 密码不能为空");
        }
        if (StrUtil.isBlank(emailConfig.getStr("from"))) {
            throw new IllegalArgumentException("发件人地址不能为空");
        }
    }

    private void validateAmazonSes(JSONObject emailConfig) {
        if (StrUtil.isBlank(emailConfig.getStr("region"))) {
            throw new IllegalArgumentException("Amazon SES 区域不能为空");
        }
        if (StrUtil.isBlank(emailConfig.getStr("access-key-id"))) {
            throw new IllegalArgumentException("Amazon SES Access Key ID 不能为空");
        }
        if (StrUtil.isBlank(emailConfig.getStr("secret-access-key"))) {
            throw new IllegalArgumentException("Amazon SES Secret Access Key 不能为空");
        }
        if (StrUtil.isBlank(emailConfig.getStr("from"))) {
            throw new IllegalArgumentException("Amazon SES 发件人地址不能为空");
        }
    }

    public String signature(JSONObject emailConfig) {
        validate(emailConfig);
        String provider = provider(emailConfig);
        String canonical;
        if (PROVIDER_AMAZON_SES.equals(provider)) {
            canonical = String.join("\n",
                    provider,
                    StrUtil.nullToEmpty(emailConfig.getStr("region")),
                    StrUtil.nullToEmpty(emailConfig.getStr("access-key-id")),
                    StrUtil.nullToEmpty(emailConfig.getStr("secret-access-key")),
                    StrUtil.nullToEmpty(emailConfig.getStr("from")),
                    StrUtil.nullToEmpty(emailConfig.getStr("configuration-set")));
        } else {
            canonical = String.join("\n",
                    provider,
                    StrUtil.nullToEmpty(emailConfig.getStr("host")),
                    String.valueOf(emailConfig.getInt("port", 587)),
                    StrUtil.nullToEmpty(emailConfig.getStr("username")),
                    StrUtil.nullToEmpty(emailConfig.getStr("password")),
                    StrUtil.nullToEmpty(emailConfig.getStr("from")),
                    String.valueOf(emailConfig.getBool("secure", false)));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成邮件配置测试签名", exception);
        }
    }

    public void sendHtml(JSONObject emailConfig, String to, String subject, String content)
            throws MessagingException {
        validate(emailConfig);
        if (PROVIDER_AMAZON_SES.equals(provider(emailConfig))) {
            sendAmazonSes(emailConfig, to, subject, content);
            return;
        }
        sendHtml(createMailSender(emailConfig), emailConfig.getStr("from"), to, subject, content);
    }

    public void sendHtml(JavaMailSenderImpl sender, String from, String to,
                         String subject, String content) throws MessagingException {
        MimeMessage mimeMessage = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);
        sender.send(mimeMessage);
    }

    private void sendAmazonSes(JSONObject emailConfig, String to, String subject, String html) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                emailConfig.getStr("access-key-id"),
                emailConfig.getStr("secret-access-key"));
        try (SesV2Client client = SesV2Client.builder()
                .region(Region.of(emailConfig.getStr("region")))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .overrideConfiguration(builder -> builder.retryPolicy(RetryPolicy.none()))
                .build()) {
            Content subjectContent = Content.builder().data(subject).charset("UTF-8").build();
            Content htmlContent = Content.builder().data(html).charset("UTF-8").build();
            Message message = Message.builder()
                    .subject(subjectContent)
                    .body(Body.builder().html(htmlContent).build())
                    .build();
            SendEmailRequest.Builder request = SendEmailRequest.builder()
                    .fromEmailAddress(emailConfig.getStr("from"))
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder().simple(message).build());
            String configurationSet = emailConfig.getStr("configuration-set");
            if (StrUtil.isNotBlank(configurationSet)) {
                request.configurationSetName(configurationSet);
            }
            client.sendEmail(request.build());
        }
    }
}
