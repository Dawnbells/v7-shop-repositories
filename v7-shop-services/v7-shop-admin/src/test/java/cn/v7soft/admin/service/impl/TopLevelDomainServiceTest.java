package cn.v7soft.admin.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.admin.dao.ITopLevelDomainDao;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ssl.PlaceholderCertHolder;
import cn.v7soft.admin.utils.NginxConfigWriter;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.TopLevelDomainRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopLevelDomainServiceTest {

    @Mock private TopLevelDomainRepository repository;
    @Mock private ITopLevelDomainDao topLevelDomainDao;
    @Mock private IFrontServerService frontServerService;
    @Mock private PlaceholderCertHolder placeholderCertHolder;
    @Mock private SubDomainService subDomainService;

    private TopLevelDomainService service;
    private MockedStatic<SaSessionUtil> saSessionUtilMock;

    @BeforeEach
    void setUp() {
        service = new TopLevelDomainService(repository, topLevelDomainDao, frontServerService, placeholderCertHolder);
        service.setSubDomainService(subDomainService);
        saSessionUtilMock = mockStatic(SaSessionUtil.class);
    }

    @AfterEach
    void tearDown() {
        saSessionUtilMock.close();
    }

    @Test
    @DisplayName("owner 本人可以删除自己的一级域名")
    void ownerCanDeleteOwnTopLevelDomain() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);

        service.delete(1L);

        verify(subDomainService).doDeleteAll(Collections.emptyList());
        verify(repository).save(domain);
    }

    @Test
    @DisplayName("同部门管理员可以删除部门成员的一级域名")
    void departmentManagerCanDeleteDepartmentTopLevelDomain() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(99L, 20L, SystemUserType.DEPARTMENT_MANAGER);

        service.delete(1L);

        verify(subDomainService).doDeleteAll(Collections.emptyList());
        verify(repository).save(domain);
    }

    @Test
    @DisplayName("删除域名时 nginx 配置未全部删除，不得删除证书或提交")
    void shouldKeepCertificateWhenNginxConfigRemains() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);
        FrontServer frontServer = FrontServer.builder().id(1L).name("front-a").build();
        when(frontServerService.listFrontServers()).thenReturn(List.of(frontServer));

        try (MockedStatic<NginxConfigWriter> nginx = mockStatic(NginxConfigWriter.class);
             MockedStatic<FileUtil> files = mockStatic(FileUtil.class)) {
            nginx.when(() -> NginxConfigWriter.nginxConfigPath("front-a", "example.com"))
                    .thenReturn("C:/test-nginx/front-a/example.com.conf");
            nginx.when(() -> NginxConfigWriter.deleteNginxIfExists("front-a", "example.com"))
                    .thenReturn(true);
            nginx.when(() -> NginxConfigWriter.existsNginxConfig("front-a", "example.com"))
                    .thenReturn(true);
            files.when(() -> FileUtil.exist("/www/certs/1/example.com"))
                    .thenReturn(true);

            assertThrows(IllegalStateException.class, () -> service.delete(1L));

            files.verify(() -> FileUtil.del("/www/certs/1/example.com"), never());
            verify(frontServerService, never()).pushAndRefreshAll();
            verify(repository, never()).save(any());
        }
    }

    @Test
    @DisplayName("多台前端服务器删除域名时，只提交一次且证书在配置删除后删除")
    void shouldDeleteAllConfigsBeforeOnePush() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);
        FrontServer frontA = FrontServer.builder().id(1L).name("front-a").build();
        FrontServer frontB = FrontServer.builder().id(2L).name("front-b").build();
        when(frontServerService.listFrontServers()).thenReturn(List.of(frontA, frontB));

        List<String> events = new ArrayList<>();
        try (MockedStatic<NginxConfigWriter> nginx = mockStatic(NginxConfigWriter.class);
             MockedStatic<FileUtil> files = mockStatic(FileUtil.class)) {
            nginx.when(() -> NginxConfigWriter.nginxConfigPath("front-a", "example.com"))
                    .thenReturn("C:/test-nginx/front-a/example.com.conf");
            nginx.when(() -> NginxConfigWriter.nginxConfigPath("front-b", "example.com"))
                    .thenReturn("C:/test-nginx/front-b/example.com.conf");
            nginx.when(() -> NginxConfigWriter.deleteNginxIfExists(anyString(), eq("example.com")))
                    .thenAnswer(invocation -> {
                        events.add("delete-config-" + invocation.getArgument(0));
                        return true;
                    });
            nginx.when(() -> NginxConfigWriter.existsNginxConfig(anyString(), eq("example.com")))
                    .thenReturn(false);
            files.when(() -> FileUtil.exist("/www/certs/1/example.com"))
                    .thenReturn(true, false);
            files.when(() -> FileUtil.del("/www/certs/1/example.com"))
                    .thenAnswer(invocation -> {
                        events.add("delete-cert");
                        return true;
                    });
            doAnswer(invocation -> {
                events.add("push");
                return null;
            }).when(frontServerService).pushAndRefreshAll();

            service.delete(1L);

            verify(frontServerService, times(1)).pushAndRefreshAll();
            assertTrue(events.indexOf("delete-config-front-a") < events.indexOf("delete-cert"));
            assertTrue(events.indexOf("delete-config-front-b") < events.indexOf("delete-cert"));
            assertTrue(events.indexOf("delete-cert") < events.indexOf("push"));
            verify(repository).save(domain);
        }
    }

    @Test
    @DisplayName("push 失败时不应完成域名删除")
    void shouldNotMarkDomainDeletedWhenPushFails() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);
        FrontServer frontServer = FrontServer.builder().id(1L).name("front-a").build();
        when(frontServerService.listFrontServers()).thenReturn(List.of(frontServer));

        try (MockedStatic<NginxConfigWriter> nginx = mockStatic(NginxConfigWriter.class);
             MockedStatic<FileUtil> files = mockStatic(FileUtil.class)) {
            nginx.when(() -> NginxConfigWriter.nginxConfigPath("front-a", "example.com"))
                    .thenReturn("C:/test-nginx/front-a/example.com.conf");
            nginx.when(() -> NginxConfigWriter.deleteNginxIfExists("front-a", "example.com"))
                    .thenReturn(true);
            nginx.when(() -> NginxConfigWriter.existsNginxConfig("front-a", "example.com"))
                    .thenReturn(false);
            files.when(() -> FileUtil.exist("/www/certs/1/example.com"))
                    .thenReturn(true, false);
            files.when(() -> FileUtil.del("/www/certs/1/example.com"))
                    .thenReturn(true);
            doThrow(new IllegalStateException("push failed"))
                    .when(frontServerService).pushAndRefreshAll();

            assertThrows(IllegalStateException.class, () -> service.delete(1L));
            verify(repository, never()).save(any());
        }
    }

    @Test
    @DisplayName("无关员工不能删除他人的一级域名")
    void unrelatedEmployeeCannotDeleteTopLevelDomain() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(99L, 30L, SystemUserType.EMPLOYEE);

        assertThrows(RuntimeException.class, () -> service.delete(1L));

        verify(subDomainService, never()).doDeleteAll(any());
        verify(repository, never()).save(any());
    }

    private void mockDomain(TopLevelDomain domain) {
        when(repository.findById(domain.getId())).thenReturn(Optional.of(domain));
        lenient().when(frontServerService.listFrontServers()).thenReturn(Collections.emptyList());
    }

    private TopLevelDomain domainWithOwner(Long domainId, Long ownerId, Long ownerDepartmentId) {
        Department department = Department.builder().id(ownerDepartmentId).build();
        SystemUser owner = SystemUser.builder().id(ownerId).department(department).build();
        return TopLevelDomain.builder()
                .id(domainId)
                .name("example.com")
                .companyId(1L)
                .owner(owner)
                .subDomains(Collections.emptyList())
                .build();
    }

    private void mockLoginUser(Long userId, Long departmentId, SystemUserType userType) {
        SystemUserDto user = SystemUserDto.builder()
                .id(String.valueOf(userId))
                .departmentId(departmentId)
                .userType(userType)
                .build();
        saSessionUtilMock.when(SaSessionUtil::getLoginUser).thenReturn(user);
    }
}
