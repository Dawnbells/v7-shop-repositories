package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.dao.ITopLevelDomainDao;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ssl.PlaceholderCertHolder;
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
