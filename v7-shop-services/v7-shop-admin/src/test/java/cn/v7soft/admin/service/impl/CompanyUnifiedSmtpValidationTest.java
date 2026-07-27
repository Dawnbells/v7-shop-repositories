package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.DynamicConfigRepository;
import cn.v7soft.dao.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyUnifiedSmtpValidationTest {

    @Mock
    private DynamicConfigRepository dynamicConfigRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private EmailSmtpSupport smtpSupport;
    private EmailAwareDynamicConfigService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(7L, null);
        smtpSupport = new EmailSmtpSupport();
        service = new EmailAwareDynamicConfigService(
                dynamicConfigRepository,
                departmentRepository,
                smtpSupport);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsUnifiedModeWithoutSuccessfulTestSignature() {
        JSONObject config = companyConfig();

        assertThatThrownBy(() -> service.saveConfig("email", null, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尚未通过测试");
    }

    @Test
    void savesUnifiedModeWhenSignatureMatchesCurrentCredentials() {
        JSONObject config = companyConfig();
        JSONObject email = config.getJSONObject("email");
        email.set("smtp-test-signature", smtpSupport.signature(email));
        when(dynamicConfigRepository.findCompanyConfig("email", 7L)).thenReturn(Optional.empty());

        service.saveConfig("email", null, config);

        verify(dynamicConfigRepository).save(any());
    }

    private JSONObject companyConfig() {
        JSONObject email = new JSONObject()
                .set("open", true)
                .set("smtp-mode", "COMPANY_UNIFIED")
                .set("host", "smtp.example.com")
                .set("port", 587)
                .set("username", "user")
                .set("password", "password")
                .set("from", "sender@example.com")
                .set("secure", false);
        return new JSONObject().set("email", email);
    }
}
