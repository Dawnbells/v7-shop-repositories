package cn.v7soft.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import cn.v7soft.admin.controller.req.BatchEditMerchandiseRequest;
import cn.v7soft.admin.controller.resp.BatchEditMerchandiseResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.dao.utils.SaSessionUtil;

@ExtendWith(MockitoExtension.class)
class ProductServiceBatchEditMerchandiseTest {

    @Mock private ProductRepository repository;
    @Mock private IProductSKUService productSKUService;
    @Mock private ILanguageService languageService;
    @Mock private ICountryService countryService;
    @Mock private IMultimediaFileService multimediaFileService;
    @Mock private SpuRepository spuRepository;
    @Mock private AsyncTaskRepository asyncTaskRepository;
    @Mock private TranslateTaskMetrics translateTaskMetrics;
    @Mock private AiCreditsService aiCreditsService;
    @Mock private IAiAccountService aiAccountService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ProductService service;
    private MockedStatic<SaSessionUtil> saSessionUtilMock;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                repository,
                productSKUService,
                languageService,
                countryService,
                multimediaFileService,
                spuRepository,
                asyncTaskRepository,
                translateTaskMetrics,
                aiCreditsService,
                aiAccountService,
                eventPublisher);
        saSessionUtilMock = org.mockito.Mockito.mockStatic(SaSessionUtil.class);
    }

    @AfterEach
    void tearDown() {
        saSessionUtilMock.close();
    }

    @Test
    void ownedAllOnlyUsesCurrentOwnersSpusAndIncludesInvalidProducts() {
        mockLoginUser(100L, 10L, SystemUserType.EMPLOYEE);
        Spu firstSpu = spu(10L, 100L, 10L);
        Spu secondSpu = spu(11L, 100L, 10L);
        Product first = product(1L, "PT=A/B", firstSpu, StatusEnum.VALID);
        Product invalid = product(2L, "PT=A/B", secondSpu, StatusEnum.INVALID);
        when(spuRepository.findIdsByOwnerId(100L)).thenReturn(List.of(10L, 11L));
        when(repository.findAllBySpuIdIn(anyCollection())).thenReturn(List.of(first, invalid));

        BatchEditMerchandiseResponse response = service.batchEditMerchandise(
                request(BatchEditMerchandiseRequest.Scope.OWNED_ALL,
                        BatchEditMerchandiseRequest.Operation.ADD, "PT=A/B", "X"));

        assertEquals("PT=A/B/X", first.getMerchandise());
        assertEquals("PT=A/B/X", invalid.getMerchandise());
        assertEquals(2, response.getTargetSpuCount());
        assertEquals(2, response.getTargetProductCount());
        assertEquals(2, response.getMatchedProductCount());
        assertEquals(0, response.getOriginalMismatchCount());
        assertEquals(2, response.getUpdatedProductCount());
        verify(spuRepository).refreshUpdateTime(10L);
        verify(spuRepository).refreshUpdateTime(11L);
    }

    @Test
    void selectedModeAllowsDepartmentManagerAndRemovesAllExactMatches() {
        mockLoginUser(100L, 10L, SystemUserType.DEPARTMENT_MANAGER);
        Spu targetSpu = spu(10L, 200L, 10L);
        Product product = product(1L, "PT=A/B/A", targetSpu, StatusEnum.VALID);
        when(spuRepository.findAllById(List.of(10L))).thenReturn(List.of(targetSpu));
        when(repository.findAllBySpuIdIn(anyCollection())).thenReturn(List.of(product));

        BatchEditMerchandiseResponse response = service.batchEditMerchandise(
                request(BatchEditMerchandiseRequest.Scope.SELECTED,
                        BatchEditMerchandiseRequest.Operation.REMOVE, "PT=A/B/A", "A"));

        assertEquals("PT=B", product.getMerchandise());
        assertEquals(1, response.getUpdatedProductCount());
        verify(spuRepository, never()).findIdsByOwnerId(100L);
    }

    @Test
    void selectedModeRejectsSpuOutsideManagementScopeBeforeLoadingProducts() {
        mockLoginUser(100L, 10L, SystemUserType.EMPLOYEE);
        Spu targetSpu = spu(10L, 200L, 10L);
        when(spuRepository.findAllById(List.of(10L))).thenReturn(List.of(targetSpu));

        assertThrows(RuntimeException.class, () -> service.batchEditMerchandise(
                request(BatchEditMerchandiseRequest.Scope.SELECTED,
                        BatchEditMerchandiseRequest.Operation.ADD, "PT=A", "X")));

        verify(repository, never()).findAllBySpuIdIn(anyCollection());
    }

    @Test
    void selectedModeRejectsMissingOrDeletedSpuBeforeLoadingProducts() {
        mockLoginUser(100L, 10L, SystemUserType.EMPLOYEE);
        when(spuRepository.findAllById(List.of(10L))).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.batchEditMerchandise(
                request(BatchEditMerchandiseRequest.Scope.SELECTED,
                        BatchEditMerchandiseRequest.Operation.ADD, "PT=A", "X")));

        verify(repository, never()).findAllBySpuIdIn(anyCollection());
    }

    @Test
    void overLengthResultLeavesEveryProductUnchanged() {
        mockLoginUser(100L, 10L, SystemUserType.EMPLOYEE);
        Spu targetSpu = spu(10L, 100L, 10L);
        Product first = product(1L, "X".repeat(512), targetSpu, StatusEnum.VALID);
        Product overLength = product(2L, "X".repeat(512), targetSpu, StatusEnum.VALID);
        when(spuRepository.findIdsByOwnerId(100L)).thenReturn(List.of(10L));
        when(repository.findAllBySpuIdIn(anyCollection())).thenReturn(List.of(first, overLength));

        assertThrows(RuntimeException.class, () -> service.batchEditMerchandise(
                request(BatchEditMerchandiseRequest.Scope.OWNED_ALL,
                        BatchEditMerchandiseRequest.Operation.ADD, "X".repeat(512), "B")));

        assertEquals("X".repeat(512), first.getMerchandise());
        assertEquals("X".repeat(512), overLength.getMerchandise());
        verify(spuRepository, never()).refreshUpdateTime(10L);
    }

    @Test
    void onlyEditsProductsWhoseOriginalNameMatchesExactly() {
        mockLoginUser(100L, 10L, SystemUserType.EMPLOYEE);
        Spu targetSpu = spu(10L, 100L, 10L);
        Product matched = product(1L, "PT=A", targetSpu, StatusEnum.VALID);
        Product differentCase = product(2L, "PT=a", targetSpu, StatusEnum.VALID);
        Product substringOnly = product(3L, "前缀PT=A后缀", targetSpu, StatusEnum.VALID);
        when(spuRepository.findIdsByOwnerId(100L)).thenReturn(List.of(10L));
        when(repository.findAllBySpuIdIn(anyCollection())).thenReturn(
                List.of(matched, differentCase, substringOnly));

        BatchEditMerchandiseResponse response = service.batchEditMerchandise(
                request(BatchEditMerchandiseRequest.Scope.OWNED_ALL,
                        BatchEditMerchandiseRequest.Operation.ADD, "PT=A", "B"));

        assertEquals("PT=A/B", matched.getMerchandise());
        assertEquals("PT=a", differentCase.getMerchandise());
        assertEquals("前缀PT=A后缀", substringOnly.getMerchandise());
        assertEquals(1, response.getMatchedProductCount());
        assertEquals(2, response.getOriginalMismatchCount());
        assertEquals(1, response.getUpdatedProductCount());
        verify(spuRepository).refreshUpdateTime(10L);
    }

    @Test
    void reportsFieldNotFoundOnlyAmongOriginalNameMatches() {
        mockLoginUser(100L, 10L, SystemUserType.EMPLOYEE);
        Spu targetSpu = spu(10L, 100L, 10L);
        Product matched = product(1L, "PT=A", targetSpu, StatusEnum.VALID);
        Product mismatch = product(2L, "PT=B", targetSpu, StatusEnum.VALID);
        when(spuRepository.findIdsByOwnerId(100L)).thenReturn(List.of(10L));
        when(repository.findAllBySpuIdIn(anyCollection())).thenReturn(List.of(matched, mismatch));

        BatchEditMerchandiseResponse response = service.batchEditMerchandise(
                request(BatchEditMerchandiseRequest.Scope.OWNED_ALL,
                        BatchEditMerchandiseRequest.Operation.REMOVE, "PT=A", "B"));

        assertEquals(0, response.getUpdatedProductCount());
        assertEquals(1, response.getNotFoundCount());
        assertEquals(1, response.getOriginalMismatchCount());
        verify(spuRepository, never()).refreshUpdateTime(10L);
    }

    @Test
    void keepEmptyReportsEmptiedProduct() {
        mockLoginUser(100L, 10L, SystemUserType.EMPLOYEE);
        Spu targetSpu = spu(10L, 100L, 10L);
        Product product = product(1L, "PT=A", targetSpu, StatusEnum.VALID);
        when(spuRepository.findIdsByOwnerId(100L)).thenReturn(List.of(10L));
        when(repository.findAllBySpuIdIn(anyCollection())).thenReturn(List.of(product));
        BatchEditMerchandiseRequest request = request(
                BatchEditMerchandiseRequest.Scope.OWNED_ALL,
                BatchEditMerchandiseRequest.Operation.REMOVE,
                "PT=A",
                "A");
        request.setEmptyResultPolicy(BatchEditMerchandiseRequest.EmptyResultPolicy.KEEP_EMPTY);

        BatchEditMerchandiseResponse response = service.batchEditMerchandise(request);

        assertEquals("PT=", product.getMerchandise());
        assertEquals(1, response.getUpdatedProductCount());
        assertEquals(1, response.getEmptiedProductCount());
    }

    private BatchEditMerchandiseRequest request(
            BatchEditMerchandiseRequest.Scope scope,
            BatchEditMerchandiseRequest.Operation operation,
            String originalMerchandise,
            String field) {
        BatchEditMerchandiseRequest request = new BatchEditMerchandiseRequest();
        request.setScope(scope);
        request.setSpuIds(scope == BatchEditMerchandiseRequest.Scope.SELECTED ? List.of(10L) : null);
        request.setOperation(operation);
        request.setOriginalMerchandise(originalMerchandise);
        request.setField(field);
        request.setDelimiter("/");
        request.setEmptyResultPolicy(BatchEditMerchandiseRequest.EmptyResultPolicy.SKIP);
        return request;
    }

    private void mockLoginUser(Long userId, Long departmentId, SystemUserType userType) {
        SystemUserDto user = SystemUserDto.builder()
                .id(String.valueOf(userId))
                .departmentId(departmentId)
                .userType(userType)
                .accessDepartmentIds(List.of(departmentId))
                .parentDepartmentIds(List.of())
                .build();
        saSessionUtilMock.when(SaSessionUtil::getLoginUser).thenReturn(user);
    }

    private Spu spu(Long id, Long ownerId, Long departmentId) {
        Department department = Department.builder().id(departmentId).build();
        SystemUser owner = SystemUser.builder().id(ownerId).department(department).build();
        return Spu.builder().id(id).owner(owner).build();
    }

    private Product product(Long id, String merchandise, Spu spu, StatusEnum status) {
        return Product.builder()
                .id(id)
                .merchandise(merchandise)
                .spu(spu)
                .status(status)
                .build();
    }
}
