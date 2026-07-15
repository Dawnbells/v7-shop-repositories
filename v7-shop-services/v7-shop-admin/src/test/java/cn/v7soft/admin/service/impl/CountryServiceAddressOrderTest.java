package cn.v7soft.admin.service.impl;

import cn.v7soft.dao.entities.meta.CountryMeta;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.enums.AddressOrder;
import cn.v7soft.dao.repositories.primary.CountryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CountryServiceAddressOrderTest {

    @Test
    void resolvesAddressOrdersWithOneNormalizedBatchQuery() {
        CountryRepository repository = mock(CountryRepository.class);
        CountryService service = new CountryService(repository);
        when(repository.findAllByCodeIn(Set.of("TW", "DE", "XX")))
                .thenReturn(List.of(
                        country("TW", AddressOrder.FORWARD),
                        country("DE", null)
                ));

        Map<String, AddressOrder> result =
                service.getAddressOrders(List.of("tw", "DE", "xx", " "));

        assertThat(result)
                .containsEntry("TW", AddressOrder.FORWARD)
                .containsEntry("DE", AddressOrder.REVERSE)
                .containsEntry("XX", AddressOrder.REVERSE);
        verify(repository).findAllByCodeIn(Set.of("TW", "DE", "XX"));
    }

    private Country country(String code, AddressOrder addressOrder) {
        return Country.builder()
                .name(code)
                .code(code)
                .continentCode("EU")
                .countryMeta(CountryMeta.builder()
                        .addressOrder(addressOrder)
                        .build())
                .build();
    }
}
