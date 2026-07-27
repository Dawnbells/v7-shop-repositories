package cn.v7soft.admin.service.email;

import cn.hutool.json.JSONObject;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.DynamicConfig;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.DynamicConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEmailConfigurationResolverTest {

    private static final long COMPANY_ID = 7L;
    private static final long DEPARTMENT_ID = 10L;
    private static final long PARENT_ID = 20L;

    @Mock
    private DynamicConfigRepository dynamicConfigRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private final Map<Long, JSONObject> departmentConfigs = new HashMap<>();
    private JSONObject companyConfig;
    private OrderEmailConfigurationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OrderEmailConfigurationResolver(dynamicConfigRepository, departmentRepository);
        Department parent = Department.builder().id(PARENT_ID).name("parent").build();
        Department current = Department.builder().id(DEPARTMENT_ID).name("current").parent(parent).build();
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(current));
        when(dynamicConfigRepository.findDepartmentConfig(eq("email"), anyLong(), eq(COMPANY_ID)))
                .thenAnswer(invocation -> entity(departmentConfigs.get(invocation.getArgument(1))));
        when(dynamicConfigRepository.findCompanyConfig("email", COMPANY_ID))
                .thenAnswer(invocation -> entity(companyConfig));
    }

    @Test
    void unifiedModeUsesCompanySmtpAndCurrentDepartmentTemplate() {
        companyConfig = config(smtp(true, "COMPANY_UNIFIED", "company.smtp"), template("en", "company"));
        departmentConfigs.put(DEPARTMENT_ID,
                config(new JSONObject().set("state", "ENABLED").set("host", "department.smtp"),
                        template("en", "department")));

        ResolvedOrderEmailConfig result = resolver.resolve(DEPARTMENT_ID, COMPANY_ID, "en");

        assertThat(result.enabled()).isTrue();
        assertThat(result.smtp().getStr("host")).isEqualTo("company.smtp");
        assertThat(result.smtpSource()).isEqualTo("company:unified");
        assertThat(result.template().getStr("subject")).isEqualTo("department");
        assertThat(result.templateSource()).isEqualTo("department:10:en");
    }

    @Test
    void explicitDepartmentDisableWinsInUnifiedMode() {
        companyConfig = config(smtp(true, "COMPANY_UNIFIED", "company.smtp"), template("en", "company"));
        departmentConfigs.put(DEPARTMENT_ID,
                config(new JSONObject().set("state", "DISABLED"), null));

        ResolvedOrderEmailConfig result = resolver.resolve(DEPARTMENT_ID, COMPANY_ID, "en");

        assertThat(result.enabled()).isFalse();
        assertThat(result.smtpSource()).isEqualTo("department:10");
    }

    @Test
    void inheritanceUsesParentSmtpButCurrentDefaultTemplateBeforeParentExactLanguage() {
        companyConfig = config(smtp(true, "DEPARTMENT_INHERITANCE", "company.smtp"), template("de", "company"));
        departmentConfigs.put(DEPARTMENT_ID, config(
                new JSONObject().set("state", "INHERIT"),
                new JSONObject().set("en", usableTemplate("current-default", true))));
        departmentConfigs.put(PARENT_ID, config(
                smtpState("ENABLED", "parent.smtp"),
                template("de", "parent-de")));

        ResolvedOrderEmailConfig result = resolver.resolve(DEPARTMENT_ID, COMPANY_ID, "de");

        assertThat(result.enabled()).isTrue();
        assertThat(result.smtp().getStr("host")).isEqualTo("parent.smtp");
        assertThat(result.smtpSource()).isEqualTo("department:20");
        assertThat(result.template().getStr("subject")).isEqualTo("current-default");
        assertThat(result.templateSource()).isEqualTo("department:10:en");
    }

    @Test
    void fallsBackToCompanyLanguageTemplateWhenDepartmentsHaveNoUsableTemplate() {
        companyConfig = config(smtp(true, "DEPARTMENT_INHERITANCE", "company.smtp"), template("de", "company-de"));
        departmentConfigs.put(DEPARTMENT_ID, config(
                new JSONObject().set("state", "INHERIT"),
                new JSONObject().set("de", new JSONObject().set("subject", "").set("content", ""))));

        ResolvedOrderEmailConfig result = resolver.resolve(DEPARTMENT_ID, COMPANY_ID, "de");

        assertThat(result.template().getStr("subject")).isEqualTo("company-de");
        assertThat(result.templateSource()).isEqualTo("company:de");
    }

    @Test
    void legacyOpenFalseIsAnExplicitDisable() {
        companyConfig = config(smtp(true, "DEPARTMENT_INHERITANCE", "company.smtp"), template("en", "company"));
        departmentConfigs.put(DEPARTMENT_ID, config(new JSONObject().set("open", false), null));

        ResolvedOrderEmailConfig result = resolver.resolve(DEPARTMENT_ID, COMPANY_ID, "en");

        assertThat(result.enabled()).isFalse();
        assertThat(result.smtpSource()).isEqualTo("department:10:disabled");
    }

    private Optional<DynamicConfig> entity(JSONObject value) {
        return value == null
                ? Optional.empty()
                : Optional.of(DynamicConfig.builder().configName("email").configValue(value).build());
    }

    private JSONObject config(JSONObject email, JSONObject templates) {
        JSONObject config = new JSONObject().set("email", email);
        if (templates != null) {
            config.set("email-template", templates);
        }
        return config;
    }

    private JSONObject smtp(boolean open, String mode, String host) {
        return smtpState(null, host)
                .set("open", open)
                .set("smtp-mode", mode);
    }

    private JSONObject smtpState(String state, String host) {
        JSONObject smtp = new JSONObject()
                .set("host", host)
                .set("port", 587)
                .set("username", "user")
                .set("password", "password")
                .set("from", "sender@example.com")
                .set("secure", false);
        if (state != null) {
            smtp.set("state", state);
        }
        return smtp;
    }

    private JSONObject template(String language, String subject) {
        return new JSONObject().set(language, usableTemplate(subject, false));
    }

    private JSONObject usableTemplate(String subject, boolean isDefault) {
        return new JSONObject()
                .set("default", isDefault)
                .set("subject", subject)
                .set("content", "<p>" + subject + "</p>");
    }
}
