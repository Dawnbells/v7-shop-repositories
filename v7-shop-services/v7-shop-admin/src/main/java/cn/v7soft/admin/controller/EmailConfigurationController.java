package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.EmailSmtpTestRequest;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.admin.service.email.EmailSmtpMode;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.admin.service.email.OrderEmailConfigurationResolver;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config-center/email")
@RequiredArgsConstructor
public class EmailConfigurationController {

    private static final String TEST_SUBJECT = "V7 Shop email configuration test";
    private static final String TEST_CONTENT = """
            <p>This is a V7 Shop email configuration test.</p>
            <p>If you received this message, the credentials, sender and delivery path are working.</p>
            """;

    private final EmailSmtpSupport smtpSupport;
    private final OrderEmailConfigurationResolver configurationResolver;
    private final IDynamicConfigService dynamicConfigService;

    @SaCheckLogin
    @PostMapping("/test")
    public JSONObject testCompanySmtp(@Valid @RequestBody EmailSmtpTestRequest request) throws Exception {
        requireCompanyAdmin();
        JSONObject email = request.getEmailConfig();
        smtpSupport.sendHtml(email, request.getRecipient(), TEST_SUBJECT, TEST_CONTENT);
        return new JSONObject()
                .set("smtpTestSignature", smtpSupport.signature(email));
    }

    @SaCheckLogin
    @GetMapping("/policy")
    public JSONObject getPolicy() {
        Long companyId = TenantContext.getCurrentTenant();
        EmailSmtpMode mode = configurationResolver.getCompanySmtpMode(companyId);
        JSONObject companyConfig = dynamicConfigService.getConfigValue("email", null, companyId);
        JSONObject companyEmail = companyConfig.getJSONObject("email");
        boolean tested = false;
        if (companyEmail != null) {
            try {
                tested = smtpSupport.signature(companyEmail)
                        .equals(companyEmail.getStr("smtp-test-signature"));
            } catch (IllegalArgumentException ignored) {
                tested = false;
            }
        }
        return new JSONObject()
                .set("smtpMode", mode.name())
                .set("companySmtpTested", tested);
    }

    private void requireCompanyAdmin() {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        if (user == null || !user.isAdmin()) {
            throw new IllegalArgumentException("只有公司管理员可以测试公司邮件发送配置");
        }
    }
}
