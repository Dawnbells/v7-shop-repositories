package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ICloudPlatformAccountService;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ISpuService;
import cn.v7soft.admin.service.IThemeCustomService;
import cn.v7soft.admin.service.IWebsiteService;
import cn.v7soft.admin.service.ssl.PlaceholderCertHolder;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.properties.ThemeEditorProperty;
import cn.v7soft.dao.repositories.primary.PixelAccountRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.SubDomainRepository;
import cn.v7soft.dao.repositories.primary.SubDomainSpuLandingPageRepository;
import cn.v7soft.dao.repositories.primary.SubDomainSpuPixelRepository;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubDomainServiceTest {

    @Mock private SubDomainRepository repository;
    @Mock private IThemeCustomService themeCustomService;
    @Mock private IWebsiteService websiteService;
    @Mock private IFrontServerService frontServerService;
    @Mock private ICloudPlatformAccountService cloudPlatformAccountService;
    @Mock private SubDomainSpuPixelRepository subDomainSpuPixelRepository;
    @Mock private SubDomainSpuLandingPageRepository subDomainSpuLandingPageRepository;
    @Mock private ISpuService spuService;
    @Mock private ThemeEditorProperty themeEditorProperty;
    @Mock private ProductRepository productRepository;
    @Mock private PixelAccountRepository pixelAccountRepository;
    @Mock private PlaceholderCertHolder placeholderCertHolder;

    private SubDomainService service;
    private MockedStatic<SaSessionUtil> saSessionUtilMock;

    @BeforeEach
    void setUp() {
        service = new SubDomainService(
                repository,
                themeCustomService,
                websiteService,
                frontServerService,
                cloudPlatformAccountService,
                subDomainSpuPixelRepository,
                subDomainSpuLandingPageRepository,
                spuService,
                themeEditorProperty,
                productRepository,
                pixelAccountRepository,
                placeholderCertHolder);
        saSessionUtilMock = mockStatic(SaSessionUtil.class);
    }

    @AfterEach
    void tearDown() {
        WebsiteContext.clear();
        saSessionUtilMock.close();
    }

    @Test
    @DisplayName("直接删除子域名时，父级域名 owner 本人可以删除")
    void parentDomainOwnerCanDeleteSubDomain() {
        SubDomain subDomain = subDomainWithParentOwner(1L, 10L, 20L, null);
        mockSubDomain(subDomain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);

        service.deleteAll(List.of(1L));

        verify(repository).saveAndFlush(subDomain);
    }

    @Test
    @DisplayName("直接删除子域名时，无关员工不能删除")
    void unrelatedEmployeeCannotDeleteSubDomain() {
        SubDomain subDomain = subDomainWithParentOwner(1L, 10L, 20L, null);
        mockSubDomain(subDomain);
        mockLoginUser(99L, 30L, SystemUserType.EMPLOYEE);

        assertThrows(RuntimeException.class, () -> service.deleteAll(List.of(1L)));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("商城域名解绑仍必须属于当前商城")
    void unbindWebsiteDomainRequiresCurrentWebsite() {
        SubDomain subDomain = subDomainWithParentOwner(1L, 10L, 20L, 200L);
        mockSubDomain(subDomain);
        mockLoginUser(10L, 99L, SystemUserType.EMPLOYEE);
        WebsiteContext.set("admin.example.com", true, 100L);

        assertThrows(RuntimeException.class, () -> service.unbindWebsiteDomains(List.of(1L)));

        verify(repository, never()).saveAndFlush(any());
    }

    private void mockSubDomain(SubDomain subDomain) {
        when(repository.findById(subDomain.getId())).thenReturn(Optional.of(subDomain));
    }

    private SubDomain subDomainWithParentOwner(Long subDomainId, Long ownerId, Long ownerDepartmentId, Long websiteId) {
        Department department = Department.builder().id(ownerDepartmentId).build();
        SystemUser owner = SystemUser.builder().id(ownerId).department(department).build();
        TopLevelDomain parentDomain = TopLevelDomain.builder()
                .id(99L)
                .name("example.com")
                .owner(owner)
                .build();
        Website website = websiteId == null ? null : Website.builder().id(websiteId).build();
        return SubDomain.builder()
                .id(subDomainId)
                .name("www")
                .fullName("www.example.com")
                .parentDomain(parentDomain)
                .website(website)
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
