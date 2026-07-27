package cn.v7soft.admin.service.email;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.DynamicConfig;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.DynamicConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderEmailConfigurationResolver {

    private static final String EMAIL_CONFIG_NAME = "email";

    private final DynamicConfigRepository dynamicConfigRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public ResolvedOrderEmailConfig resolve(Long departmentId, Long companyId, String languageCode) {
        ConfigLevel company = companyLevel(companyId);
        List<ConfigLevel> departments = departmentLevels(departmentId, companyId);
        EmailSmtpMode smtpMode = EmailSmtpMode.from(emailNode(company.config()).getStr("smtp-mode"));

        SmtpResolution smtpResolution = smtpMode == EmailSmtpMode.COMPANY_UNIFIED
                ? resolveUnifiedSmtp(departments, company)
                : resolveInheritedSmtp(departments, company);
        if (!smtpResolution.enabled()) {
            return ResolvedOrderEmailConfig.disabled(smtpResolution.source());
        }

        TemplateResolution template = resolveTemplate(departments, company, languageCode);
        return new ResolvedOrderEmailConfig(
                true,
                smtpResolution.smtp(),
                smtpResolution.source(),
                template.template(),
                template.source());
    }

    @Transactional(readOnly = true)
    public EmailSmtpMode getCompanySmtpMode(Long companyId) {
        return EmailSmtpMode.from(emailNode(companyLevel(companyId).config()).getStr("smtp-mode"));
    }

    private SmtpResolution resolveUnifiedSmtp(List<ConfigLevel> departments, ConfigLevel company) {
        EffectiveState state = resolveDepartmentState(departments, company);
        if (!state.enabled()) {
            return new SmtpResolution(false, null, state.source());
        }
        JSONObject companyEmail = emailNode(company.config());
        if (!companyEmail.getBool("open", false)) {
            return new SmtpResolution(false, null, "company:disabled");
        }
        return new SmtpResolution(true, companyEmail, "company:unified");
    }

    private SmtpResolution resolveInheritedSmtp(List<ConfigLevel> departments, ConfigLevel company) {
        for (ConfigLevel level : departments) {
            if (level.config() == null) {
                continue;
            }
            JSONObject email = emailNode(level.config());
            DepartmentEmailState state = DepartmentEmailState.from(email);
            if (state == DepartmentEmailState.DISABLED) {
                return new SmtpResolution(false, null, level.source() + ":disabled");
            }
            if (state == DepartmentEmailState.ENABLED) {
                return new SmtpResolution(true, email, level.source());
            }
        }

        JSONObject companyEmail = emailNode(company.config());
        if (company.config() == null || !companyEmail.getBool("open", false)) {
            return new SmtpResolution(false, null, "company:disabled");
        }
        return new SmtpResolution(true, companyEmail, "company");
    }

    private EffectiveState resolveDepartmentState(List<ConfigLevel> departments, ConfigLevel company) {
        for (ConfigLevel level : departments) {
            if (level.config() == null) {
                continue;
            }
            DepartmentEmailState state = DepartmentEmailState.from(emailNode(level.config()));
            if (state == DepartmentEmailState.DISABLED) {
                return new EffectiveState(false, level.source());
            }
            if (state == DepartmentEmailState.ENABLED) {
                return new EffectiveState(true, level.source());
            }
        }
        boolean companyEnabled = company.config() != null && emailNode(company.config()).getBool("open", false);
        return new EffectiveState(companyEnabled, companyEnabled ? "company" : "company:disabled");
    }

    private TemplateResolution resolveTemplate(List<ConfigLevel> departments, ConfigLevel company,
                                               String languageCode) {
        for (ConfigLevel level : departments) {
            TemplateResolution resolution = templateAtLevel(level, languageCode);
            if (resolution.template() != null) {
                return resolution;
            }
        }
        TemplateResolution companyTemplate = templateAtLevel(company, languageCode);
        if (companyTemplate.template() != null) {
            return companyTemplate;
        }
        return new TemplateResolution(null, "built-in");
    }

    private TemplateResolution templateAtLevel(ConfigLevel level, String languageCode) {
        if (level.config() == null) {
            return new TemplateResolution(null, level.source());
        }
        JSONObject templates = level.config().getJSONObject("email-template");
        if (templates == null) {
            return new TemplateResolution(null, level.source());
        }

        if (StrUtil.isNotBlank(languageCode)) {
            JSONObject exact = templates.getJSONObject(languageCode);
            if (isUsableTemplate(exact)) {
                return new TemplateResolution(exact, level.source() + ":" + languageCode);
            }
        }

        Optional<String> defaultLanguage = templates.keySet().stream()
                .filter(code -> {
                    JSONObject candidate = templates.getJSONObject(code);
                    return candidate != null
                            && candidate.getBool("default", false)
                            && isUsableTemplate(candidate);
                })
                .findFirst();
        return defaultLanguage
                .map(code -> new TemplateResolution(templates.getJSONObject(code), level.source() + ":" + code))
                .orElseGet(() -> new TemplateResolution(null, level.source()));
    }

    private boolean isUsableTemplate(JSONObject template) {
        return template != null
                && StrUtil.isNotBlank(template.getStr("subject"))
                && StrUtil.isNotBlank(template.getStr("content"));
    }

    private List<ConfigLevel> departmentLevels(Long departmentId, Long companyId) {
        List<ConfigLevel> result = new ArrayList<>();
        if (departmentId == null) {
            return result;
        }
        Department current = departmentRepository.findById(departmentId).orElse(null);
        Long currentId = departmentId;
        while (currentId != null) {
            JSONObject config = dynamicConfigRepository
                    .findDepartmentConfig(EMAIL_CONFIG_NAME, currentId, companyId)
                    .map(DynamicConfig::getConfigValue)
                    .orElse(null);
            result.add(new ConfigLevel(config, "department:" + currentId));
            if (current == null) {
                break;
            }
            current = current.getParent();
            currentId = current == null ? null : current.getId();
        }
        return result;
    }

    private ConfigLevel companyLevel(Long companyId) {
        JSONObject config = dynamicConfigRepository
                .findCompanyConfig(EMAIL_CONFIG_NAME, companyId)
                .map(DynamicConfig::getConfigValue)
                .orElse(null);
        return new ConfigLevel(config, "company");
    }

    private JSONObject emailNode(JSONObject config) {
        if (config == null) {
            return new JSONObject();
        }
        JSONObject email = config.getJSONObject("email");
        return email == null ? new JSONObject() : email;
    }

    private record ConfigLevel(JSONObject config, String source) {
    }

    private record SmtpResolution(boolean enabled, JSONObject smtp, String source) {
    }

    private record EffectiveState(boolean enabled, String source) {
    }

    private record TemplateResolution(JSONObject template, String source) {
    }
}
