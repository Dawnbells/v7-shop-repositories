package cn.v7soft.admin.service.email;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

@Component
public class EmailSmtpSupport {

    public JavaMailSenderImpl createMailSender(JSONObject emailConfig) {
        validate(emailConfig);
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

    public String signature(JSONObject emailConfig) {
        validate(emailConfig);
        String canonical = String.join("\n",
                StrUtil.nullToEmpty(emailConfig.getStr("host")),
                String.valueOf(emailConfig.getInt("port", 587)),
                StrUtil.nullToEmpty(emailConfig.getStr("username")),
                StrUtil.nullToEmpty(emailConfig.getStr("password")),
                StrUtil.nullToEmpty(emailConfig.getStr("from")),
                String.valueOf(emailConfig.getBool("secure", false)));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成 SMTP 测试签名", exception);
        }
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
}
