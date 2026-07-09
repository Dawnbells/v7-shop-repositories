package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.req.QueryProductSKURequest;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.dao.entities.primary.ProductSKU;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSKUControllerQueryTest {

    @Test
    void pageQueryAppliesSkuCodeAndNameFilters() {
        QueryProductSKURequest request = QueryProductSKURequest.builder()
                .pageNo(1)
                .build();
        request.setSkuCode("SKU-001");
        request.setName("Phone");

        TestProductSKUController controller = new TestProductSKUController(mock(IProductSKUService.class));
        QueryPageRequest<ProductSKU> pageRequest = controller.exposeConvertQueryPageRequest(request);

        @SuppressWarnings("unchecked")
        Root<ProductSKU> root = mock(Root.class);
        @SuppressWarnings("rawtypes")
        Path path = mock(Path.class);
        @SuppressWarnings("rawtypes")
        Expression stringExpression = mock(Expression.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get(anyString())).thenReturn(path);
        when(criteriaBuilder.toString(any(Expression.class))).thenReturn(stringExpression);
        when(criteriaBuilder.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(criteriaBuilder.equal(any(Expression.class), any())).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);

        pageRequest.toPredicate(root, query, criteriaBuilder);

        verify(root).get("skuCode");
        verify(root).get("name");
    }

    @Test
    void pageQueryAllowsEmptySkuCodeAndNameFilters() {
        QueryProductSKURequest request = QueryProductSKURequest.builder()
                .pageNo(1)
                .build();
        TestProductSKUController controller = new TestProductSKUController(mock(IProductSKUService.class));

        assertDoesNotThrow(() -> controller.exposeConvertQueryPageRequest(request));
    }

    private static class TestProductSKUController extends ProductSKUController {
        TestProductSKUController(IProductSKUService service) {
            super(service);
        }

        QueryPageRequest<ProductSKU> exposeConvertQueryPageRequest(QueryProductSKURequest request) {
            return super.convertQueryPageRequest(request);
        }
    }
}
