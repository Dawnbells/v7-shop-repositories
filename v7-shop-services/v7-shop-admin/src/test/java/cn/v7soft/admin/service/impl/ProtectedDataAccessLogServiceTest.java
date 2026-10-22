package cn.v7soft.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ProtectedDataAccessLog;
import cn.v7soft.dao.repositories.primary.ProtectedDataAccessLogRepository;
import cn.v7soft.dao.utils.SaSessionUtil;

@ExtendWith(MockitoExtension.class)
class ProtectedDataAccessLogServiceTest {

    @Mock
    private ProtectedDataAccessLogRepository repository;

    @InjectMocks
    private ProtectedDataAccessLogService service;

    private MockedStatic<SaSessionUtil> session;

    @BeforeEach
    void setUp() {
        SystemUserDto user = new SystemUserDto();
        user.setId("7");
        user.setName("张三");
        session = Mockito.mockStatic(SaSessionUtil.class);
        session.when(SaSessionUtil::getLoginUser).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    private ProtectedDataAccessLog captureSaved() {
        ArgumentCaptor<ProtectedDataAccessLog> captor = ArgumentCaptor.forClass(ProtectedDataAccessLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordListWritesOneLogWithUserIdsAndCount() {
        service.record("LIST", "ORDER", List.of(11L, 12L, 13L));

        ProtectedDataAccessLog saved = captureSaved();
        assertEquals(7L, saved.getUserId());
        assertEquals("张三", saved.getUserName());
        assertEquals("LIST", saved.getAction());
        assertEquals("ORDER", saved.getOrderType());
        assertEquals("11,12,13", saved.getOrderIds());
        assertEquals(3, saved.getOrderCount());
        assertNotNull(saved.getAccessTime());
    }

    @Test
    void recordStoresAtMost500IdsButCountsAll() {
        List<Long> ids = LongStream.rangeClosed(1, 620).boxed().toList();

        service.record("LIST", "TEMPORARY", ids);

        ProtectedDataAccessLog saved = captureSaved();
        String[] stored = saved.getOrderIds().split(",");
        assertEquals(500, stored.length);
        assertEquals("1", stored[0]);
        assertEquals("500", stored[499]);
        assertEquals(620, saved.getOrderCount());
        assertEquals("TEMPORARY", saved.getOrderType());
    }

    @Test
    void recordExportUsesTotalCount() {
        List<Long> ids = LongStream.rangeClosed(1, 500).boxed().toList();

        service.record("EXPORT", "ORDER", ids, 1234L);

        ProtectedDataAccessLog saved = captureSaved();
        assertEquals("EXPORT", saved.getAction());
        assertEquals("ORDER", saved.getOrderType());
        assertEquals(500, saved.getOrderIds().split(",").length);
        assertEquals(1234, saved.getOrderCount());
    }

    @Test
    void recordExportTotalCountNeverBelowIdCount() {
        service.record("EXPORT", "ORDER", List.of(1L, 2L), 1L);

        assertEquals(2, captureSaved().getOrderCount());
    }

    @Test
    void emptyOrNullIdsWriteNothing() {
        service.record("LIST", "ORDER", Collections.emptyList());
        service.record("LIST", "ORDER", null);
        service.record("EXPORT", "ORDER", Collections.emptyList(), 0L);
        List<Long> onlyNulls = new ArrayList<>(Arrays.asList(null, null));
        service.record("LIST", "ORDER", onlyNulls);

        verify(repository, never()).save(any());
    }

    @Test
    void repositoryFailureIsSwallowed() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.record("LIST", "ORDER", List.of(1L)));
    }

    @Test
    void sessionFailureIsSwallowed() {
        session.when(SaSessionUtil::getLoginUser).thenThrow(new IllegalStateException("not login"));

        assertDoesNotThrow(() -> service.record("EXPORT", "ORDER", List.of(1L), 1L));
        verify(repository, never()).save(any());
    }
}
