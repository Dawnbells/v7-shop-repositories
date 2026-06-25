package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.ViewMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderStatisticsOptionRepositoryTest {

    @Test
    void readsScalarDomainRowsFromNativeQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("a.shop.com", "b.shop.com"));
        OrderStatisticsOptionRepository repository =
                new OrderStatisticsOptionRepository(entityManager);

        List<String> result = repository.domains(companyScope(), "");

        assertThat(result).containsExactly("a.shop.com", "b.shop.com");
    }

    private OrderStatisticsAccessScope companyScope() {
        return new OrderStatisticsAccessScope(
                9L,
                101L,
                true,
                false,
                Set.of(),
                Set.of(),
                true,
                false,
                null,
                ViewMode.TEAM
        );
    }
}
