package cn.v7soft.common.controller.resp;

import cn.v7soft.dao.entities.meta.CountryMeta;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.enums.AddressOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountryResponseTest {

    @Test
    void exposesConfiguredAddressOrder() {
        Country country = Country.builder()
                .name("台湾")
                .code("TW")
                .continentCode("AS")
                .countryMeta(CountryMeta.builder()
                        .addressOrder(AddressOrder.FORWARD)
                        .build())
                .build();

        assertThat(CountryResponse.convertEntity(country).getAddressOrder())
                .isEqualTo(AddressOrder.FORWARD);
    }

    @Test
    void defaultsNullAddressOrderToReverse() {
        Country country = Country.builder()
                .name("德国")
                .code("DE")
                .continentCode("EU")
                .countryMeta(CountryMeta.builder()
                        .addressOrder(null)
                        .build())
                .build();

        assertThat(CountryResponse.convertEntity(country).getAddressOrder())
                .isEqualTo(AddressOrder.REVERSE);
    }

    @Test
    void defaultsMissingCountryMetaToReverse() {
        Country country = Country.builder()
                .name("德国")
                .code("DE")
                .continentCode("EU")
                .build();

        assertThat(CountryResponse.convertEntity(country).getAddressOrder())
                .isEqualTo(AddressOrder.REVERSE);
    }
}
