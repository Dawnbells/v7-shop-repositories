package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.GenerateSharedUrlRequest;
import cn.v7soft.admin.service.IEmployeeService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.dao.enums.LandingPageType;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.dao.repositories.primary.SubDomainRepository;
import cn.v7soft.dao.repositories.primary.SubDomainSpuLandingPageRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpuServiceTest {

    @Mock private SpuRepository repository;
    @Mock private IEmployeeService employeeService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ProductRepository productRepository;
    @Mock private SubDomainRepository subDomainRepository;
    @Mock private SubDomainSpuLandingPageRepository subDomainSpuLandingPageRepository;

    private SpuService service;
    private MockedStatic<SaSessionUtil> saSessionUtilMock;

    @BeforeEach
    void setUp() {
        service = new SpuService(
                repository,
                employeeService,
                redisTemplate,
                productRepository,
                subDomainRepository,
                subDomainSpuLandingPageRepository);
        saSessionUtilMock = org.mockito.Mockito.mockStatic(SaSessionUtil.class);
    }

    @AfterEach
    void tearDown() {
        saSessionUtilMock.close();
    }

    @Test
    void generateSharedUrlAllowsAccessibleBoundLandingPage() {
        GenerateSharedUrlRequest request = sharedUrlRequest("https://shop.example.com/product/1001", 60);
        mockLoginUser(100L, 10L, 1L, SystemUserType.EMPLOYEE, List.of(10L), List.of());
        mockSubDomain(10L, 100L, 10L);
        mockBoundLandingPage(10L, 1001L, true);
        mockSpu(1001L, 100L, 10L, 1L, false);

        String sharedUrl = service.generateSharedUrl(request);

        assertTrue(sharedUrl.startsWith("https://shop.example.com/product/1001?xyz-sid="));
    }

    @Test
    void generateSharedUrlAllowsLegacyWebsiteBoundLandingPage() {
        GenerateSharedUrlRequest request = sharedUrlRequest("https://shop.example.com/product/1001", 60);
        mockLoginUser(100L, 10L, 1L, SystemUserType.EMPLOYEE, List.of(10L), List.of());
        mockSubDomain(10L, 100L, 10L, 200L);
        mockBoundLandingPage(10L, 1001L, false);
        when(repository.findByIdAndWebsiteId(1001L, 200L)).thenReturn(Optional.of(Spu.builder().id(1001L).build()));
        mockSpu(1001L, 100L, 10L, 1L, false);

        String sharedUrl = service.generateSharedUrl(request);

        assertTrue(sharedUrl.startsWith("https://shop.example.com/product/1001?xyz-sid="));
    }

    @Test
    void generateSharedUrlRejectsDomainWithoutPermission() {
        GenerateSharedUrlRequest request = sharedUrlRequest("https://shop.example.com/product/1001", 60);
        mockLoginUser(300L, 30L, 1L, SystemUserType.EMPLOYEE, List.of(30L), List.of());
        mockSubDomain(10L, 100L, 10L);

        assertThrows(RuntimeException.class, () -> service.generateSharedUrl(request));
    }

    @Test
    void generateSharedUrlRejectsUnboundLandingPage() {
        GenerateSharedUrlRequest request = sharedUrlRequest("https://shop.example.com/product/1001", 60);
        mockLoginUser(100L, 10L, 1L, SystemUserType.EMPLOYEE, List.of(10L), List.of());
        mockSubDomain(10L, 100L, 10L, 200L);
        mockBoundLandingPage(10L, 1001L, false);
        when(repository.findByIdAndWebsiteId(1001L, 200L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.generateSharedUrl(request));
    }

    @Test
    void generateSharedUrlRejectsSpuWithoutPermission() {
        GenerateSharedUrlRequest request = sharedUrlRequest("https://shop.example.com/product/1001", 60);
        mockLoginUser(100L, 10L, 1L, SystemUserType.EMPLOYEE, List.of(10L), List.of());
        mockSubDomain(10L, 100L, 10L);
        mockBoundLandingPage(10L, 1001L, true);
        mockSpu(1001L, 200L, 20L, 1L, false);

        assertThrows(RuntimeException.class, () -> service.generateSharedUrl(request));
    }

    @Test
    void generateSharedUrlRejectsInvalidProductPath() {
        GenerateSharedUrlRequest request = sharedUrlRequest("https://shop.example.com/product/1001/extra", 60);

        assertThrows(RuntimeException.class, () -> service.generateSharedUrl(request));
    }

    @Test
    void generateSharedUrlRejectsInvalidExpireSeconds() {
        GenerateSharedUrlRequest request = sharedUrlRequest("https://shop.example.com/product/1001", 0);

        assertThrows(RuntimeException.class, () -> service.generateSharedUrl(request));
    }

    private GenerateSharedUrlRequest sharedUrlRequest(String url, long expireSeconds) {
        GenerateSharedUrlRequest request = new GenerateSharedUrlRequest();
        request.setUrl(url);
        request.setExpireSeconds(expireSeconds);
        return request;
    }

    private void mockLoginUser(Long userId, Long departmentId, Long companyId, SystemUserType userType,
                               List<Long> accessDepartmentIds, List<Long> parentDepartmentIds) {
        SystemUserDto user = SystemUserDto.builder()
                .id(String.valueOf(userId))
                .departmentId(departmentId)
                .companyId(companyId)
                .userType(userType)
                .accessDepartmentIds(accessDepartmentIds)
                .parentDepartmentIds(parentDepartmentIds)
                .build();
        saSessionUtilMock.when(SaSessionUtil::getLoginUser).thenReturn(user);
    }

    private void mockSubDomain(Long subDomainId, Long ownerId, Long ownerDepartmentId) {
        mockSubDomain(subDomainId, ownerId, ownerDepartmentId, null);
    }

    private void mockSubDomain(Long subDomainId, Long ownerId, Long ownerDepartmentId, Long websiteId) {
        Department department = Department.builder().id(ownerDepartmentId).build();
        SystemUser owner = SystemUser.builder().id(ownerId).department(department).build();
        TopLevelDomain parentDomain = TopLevelDomain.builder()
                .id(99L)
                .owner(owner)
                .build();
        Website website = websiteId == null ? null : Website.builder().id(websiteId).build();
        SubDomain subDomain = SubDomain.builder()
                .id(subDomainId)
                .fullName("shop.example.com")
                .parentDomain(parentDomain)
                .website(website)
                .build();
        when(subDomainRepository.findByFullName("shop.example.com")).thenReturn(subDomain);
    }

    private void mockBoundLandingPage(Long subDomainId, Long spuId, boolean exists) {
        when(subDomainSpuLandingPageRepository.existsBySubDomainIdAndSpuIdAndLandingPageType(
                subDomainId, spuId, LandingPageType.LAND)).thenReturn(exists);
    }

    private void mockSpu(Long spuId, Long ownerId, Long ownerDepartmentId, Long companyId, boolean isOpen) {
        Department department = Department.builder().id(ownerDepartmentId).build();
        SystemUser owner = SystemUser.builder().id(ownerId).department(department).build();
        Spu spu = Spu.builder()
                .id(spuId)
                .owner(owner)
                .companyId(companyId)
                .isOpen(isOpen)
                .build();
        when(repository.findById(spuId)).thenReturn(Optional.of(spu));
    }
}
