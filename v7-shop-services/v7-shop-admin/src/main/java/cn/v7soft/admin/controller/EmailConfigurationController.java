package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.EmailSmtpTestRequest;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.admin.service.dto.OrderEmailDto;
import cn.v7soft.admin.service.email.EmailSmtpMode;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.admin.service.email.OrderEmailConfigurationResolver;
import cn.v7soft.admin.service.email.OrderEmailRenderer;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/config-center/email")
@RequiredArgsConstructor
public class EmailConfigurationController {

    private static final DateTimeFormatter TEST_ORDER_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final EmailSmtpSupport smtpSupport;
    private final OrderEmailRenderer orderEmailRenderer;
    private final OrderEmailConfigurationResolver configurationResolver;
    private final IDynamicConfigService dynamicConfigService;

    @SaCheckLogin
    @PostMapping("/test")
    public JSONObject testCompanySmtp(@Valid @RequestBody EmailSmtpTestRequest request) throws Exception {
        requireCompanyAdmin();
        JSONObject email = request.getEmailConfig();
        OrderEmailRenderer.RenderedOrderEmail rendered = orderEmailRenderer.render(
                request.getOrderTemplate(), testOrder(request.getRecipient()));
        smtpSupport.sendHtml(email, request.getRecipient(), rendered.subject(), rendered.content());
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

    private OrderEmailDto testOrder(String recipient) {
        return OrderEmailDto.builder()
                .originOrderId("V7-" + LocalDateTime.now().format(TEST_ORDER_ID_FORMAT))
                .email(recipient)
                .firstName("Alex")
                .lastName("Johnson")
                .phone("+1 202-555-0148")
                .address("123 Market Street")
                .district("Financial District")
                .city("San Francisco")
                .province("California")
                .postalCode("94105")
                .remark("Please leave the package at the front desk.")
                .currencyCode("USD")
                .totalAmount(new BigDecimal("89.98"))
                .items(List.of(OrderEmailDto.Item.builder()
                        .specTitle("Classic Product")
                        .sellPrice(new BigDecimal("44.99"))
                        .quantity(2L)
                        .build()))
                .build();
    }
}
