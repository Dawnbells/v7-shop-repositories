package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.dao.entities.primary.DynamicConfig;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.DynamicConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAwareDynamicConfigServiceTest {

    @Mock
    private DynamicConfigRepository dynamicConfigRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private EmailAwareDynamicConfigService service;

    @BeforeEach
    void setUp() {
        service = new EmailAwareDynamicConfigService(
                dynamicConfigRepository,
                departmentRepository,
                new EmailSmtpSupport());
    }

    @Test
    void missingDepartmentRecordStaysEmptySoUiCanTreatItAsInherit() {
        when(dynamicConfigRepository.findDepartmentConfig("email", 10L, 7L))
                .thenReturn(Optional.empty());

        JSONObject value = service.getConfigValue("email", 10L, 7L);

        assertThat(value.isEmpty()).isTrue();
    }

    @Test
    void legacyOpenTrueIsExposedAsEnabledState() {
        JSONObject value = new JSONObject()
                .set("email", new JSONObject().set("open", true));
        when(dynamicConfigRepository.findDepartmentConfig("email", 10L, 7L))
                .thenReturn(Optional.of(DynamicConfig.builder()
                        .configName("email")
                        .configValue(value)
                        .build()));

        JSONObject result = service.getConfigValue("email", 10L, 7L);

        assertThat(result.getJSONObject("email").getStr("state")).isEqualTo("ENABLED");
    }

    @Test
    void legacyOpenFalseIsExposedAsDisabledState() {
        JSONObject value = new JSONObject()
                .set("email", new JSONObject().set("open", false));
        when(dynamicConfigRepository.findDepartmentConfig("email", 10L, 7L))
                .thenReturn(Optional.of(DynamicConfig.builder()
                        .configName("email")
                        .configValue(value)
                        .build()));

        JSONObject result = service.getConfigValue("email", 10L, 7L);

        assertThat(result.getJSONObject("email").getStr("state")).isEqualTo("DISABLED");
    }
}
