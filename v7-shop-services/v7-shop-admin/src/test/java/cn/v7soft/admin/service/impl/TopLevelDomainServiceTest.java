package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.dao.ITopLevelDomainDao;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ssl.PlaceholderCertHolder;
import cn.v7soft.admin.service.ssl.DomainResourceCleaner;
import cn.v7soft.dao.dto.SystemUserDto;
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

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopLevelDomainServiceTest {

    @Mock private TopLevelDomainRepository repository;
    @Mock private ITopLevelDomainDao topLevelDomainDao;
    @Mock private IFrontServerService frontServerService;
    @Mock private PlaceholderCertHolder placeholderCertHolder;
    @Mock private SubDomainService subDomainService;
    @Mock private DomainResourceCleaner resourceCleaner;

    private TopLevelDomainService service;
    private MockedStatic<SaSessionUtil> saSessionUtilMock;

    @BeforeEach
    void setUp() {
        service = new TopLevelDomainService(repository, topLevelDomainDao, frontServerService, placeholderCertHolder, resourceCleaner);
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

        verify(subDomainService).deleteChildrenForParent(Collections.emptyList());
        verify(repository).save(domain);
    }

    @Test
    @DisplayName("同部门管理员可以删除部门成员的一级域名")
    void departmentManagerCanDeleteDepartmentTopLevelDomain() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(99L, 20L, SystemUserType.DEPARTMENT_MANAGER);

        service.delete(1L);

        verify(subDomainService).deleteChildrenForParent(Collections.emptyList());
        verify(repository).save(domain);
    }

    @Test
    void cleanupFailureDoesNotCompleteDeletion() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);
        doThrow(new IllegalStateException("config remains"))
                .when(resourceCleaner).deleteDomain(eq(domain), any());
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        verify(repository, never()).save(any());
        verify(frontServerService, never()).pushAndRefreshAll();
    }

    @Test
    void shouldPublishOnlyThroughResourceCleaner() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(resourceCleaner).deleteDomain(eq(domain), any());
        service.delete(1L);
        verify(subDomainService).deleteChildrenForParent(Collections.emptyList());
        verify(subDomainService, never()).doDeleteAll(any());
        verify(frontServerService).pushAndRefreshAll();
        verify(frontServerService, never()).pushAndRefresh(any());
        verify(repository).save(domain);
    }

    @Test
    void shouldNotMarkDomainDeletedWhenPushFails() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(resourceCleaner).deleteDomain(eq(domain), any());
        doThrow(new IllegalStateException("push failed")).when(frontServerService).pushAndRefreshAll();
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        verify(repository, never()).save(any());
    }
    @Test
    @DisplayName("无关员工不能删除他人的一级域名")
    void unrelatedEmployeeCannotDeleteTopLevelDomain() {
        TopLevelDomain domain = domainWithOwner(1L, 10L, 20L);
        mockDomain(domain);
        mockLoginUser(99L, 30L, SystemUserType.EMPLOYEE);

        assertThrows(RuntimeException.class, () -> service.delete(1L));

        verify(subDomainService, never()).deleteChildrenForParent(any());
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
