package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.enums.OrderSearchPresetTimeMode;
import cn.v7soft.dao.repositories.primary.OrderSearchPresetRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSearchPresetServiceTest {

    @Mock
    private OrderSearchPresetRepository repository;

    private OrderSearchPresetService service;
    private MockedStatic<SaSessionUtil> saSessionUtil;
    private final SystemUserDto loginUser = SystemUserDto.builder()
            .id("101")
            .companyId(1L)
            .name("Alice")
            .build();

    @BeforeEach
    void setUp() {
        service = new OrderSearchPresetService(repository);
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(loginUser);
    }

    @AfterEach
    void tearDown() {
        saSessionUtil.close();
    }

    @Test
    @DisplayName("savePreset: no same-name preset creates a preset owned by current login user")
    void savePresetCreatesCurrentUserPreset() {
        JSONObject queryParams = new JSONObject();
        queryParams.set("searchType", "ORDER_ID");
        queryParams.set("keywords", "10001");
        SaveOrderSearchPresetRequest request = SaveOrderSearchPresetRequest.builder()
                .pageType(OrderSearchPresetPageType.ORDER_AUDIT)
                .timeMode(OrderSearchPresetTimeMode.ABSOLUTE)
                .name(" 每日审单 ")
                .queryParams(queryParams)
                .build();

        when(repository.findValidByOwnerAndPageTypeAndName(
                101L, OrderSearchPresetPageType.ORDER_AUDIT, "每日审单"))
                .thenReturn(Optional.empty());
        when(repository.save(any(OrderSearchPreset.class))).thenAnswer(invocation -> {
            OrderSearchPreset preset = invocation.getArgument(0);
            preset.setId(900L);
            return preset;
        });

        OrderSearchPreset result = service.savePreset(request);

        assertThat(result.getId()).isEqualTo(900L);
        assertThat(result.getOwner().getId()).isEqualTo(101L);
        assertThat(result.getName()).isEqualTo("每日审单");
        assertThat(result.getPageType()).isEqualTo(OrderSearchPresetPageType.ORDER_AUDIT);
        assertThat(result.getTimeMode()).isEqualTo(OrderSearchPresetTimeMode.ABSOLUTE);
        assertThat(result.getQueryParams().getStr("keywords")).isEqualTo("10001");
    }

    @Test
    @DisplayName("savePreset: same-name preset updates existing current-user page preset")
    void savePresetOverwritesSameNameCurrentUserPreset() {
        OrderSearchPreset existing = OrderSearchPreset.builder()
                .id(10L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_MANAGER)
                .timeMode(OrderSearchPresetTimeMode.ABSOLUTE)
                .name("常用")
                .queryParams(new JSONObject().set("keywords", "old"))
                .build();
        JSONObject queryParams = new JSONObject().set("keywords", "new");
        SaveOrderSearchPresetRequest request = SaveOrderSearchPresetRequest.builder()
                .pageType(OrderSearchPresetPageType.ORDER_MANAGER)
                .timeMode(OrderSearchPresetTimeMode.RELATIVE)
                .name("常用")
                .queryParams(queryParams)
                .build();

        when(repository.findValidByOwnerAndPageTypeAndName(
                101L, OrderSearchPresetPageType.ORDER_MANAGER, "常用"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(OrderSearchPreset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderSearchPreset result = service.savePreset(request);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getOwner().getId()).isEqualTo(101L);
        assertThat(result.getTimeMode()).isEqualTo(OrderSearchPresetTimeMode.RELATIVE);
        assertThat(result.getQueryParams().getStr("keywords")).isEqualTo("new");
    }

    @Test
    @DisplayName("listCurrentUserPresets: returns current user and page presets using repository ordering")
    void listCurrentUserPresetsUsesOwnerAndPageType() {
        OrderSearchPreset preset = OrderSearchPreset.builder()
                .id(11L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_AUDIT)
                .name("审单")
                .timeMode(OrderSearchPresetTimeMode.RELATIVE)
                .queryParams(new JSONObject())
                .build();
        when(repository.findValidByOwnerAndPageTypeOrderByUsage(
                101L, OrderSearchPresetPageType.ORDER_AUDIT))
                .thenReturn(List.of(preset));

        List<OrderSearchPreset> result = service.listCurrentUserPresets(OrderSearchPresetPageType.ORDER_AUDIT);

        assertThat(result).containsExactly(preset);
        verify(repository).findValidByOwnerAndPageTypeOrderByUsage(
                101L, OrderSearchPresetPageType.ORDER_AUDIT);
    }

    @Test
    @DisplayName("usePreset: updates lastUsedTime only for current user's preset")
    void usePresetUpdatesLastUsedTime() {
        OrderSearchPreset preset = OrderSearchPreset.builder()
                .id(12L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_AUDIT)
                .name("审单")
                .timeMode(OrderSearchPresetTimeMode.RELATIVE)
                .queryParams(new JSONObject())
                .build();
        when(repository.findValidByIdAndOwnerId(12L, 101L)).thenReturn(Optional.of(preset));
        when(repository.save(any(OrderSearchPreset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderSearchPreset result = service.usePreset(12L);

        assertThat(result.getLastUsedTime()).isNotNull();
    }

    @Test
    @DisplayName("deletePreset: soft deletes only current user's preset")
    void deletePresetSoftDeletesCurrentUserPreset() {
        OrderSearchPreset preset = OrderSearchPreset.builder()
                .id(13L)
                .owner(loginUser.toOwner())
                .pageType(OrderSearchPresetPageType.ORDER_MANAGER)
                .name("管理")
                .timeMode(OrderSearchPresetTimeMode.ABSOLUTE)
                .queryParams(new JSONObject())
                .build();
        when(repository.findValidByIdAndOwnerId(13L, 101L)).thenReturn(Optional.of(preset));

        service.deletePreset(13L);

        ArgumentCaptor<OrderSearchPreset> captor = ArgumentCaptor.forClass(OrderSearchPreset.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusEnum.DELETED);
    }
}
