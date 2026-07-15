package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.req.EditCountryRequest;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.enums.AddressOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CountryControllerAddressOrderTest {

    @Test
    void savesConfiguredAddressOrder() {
        CountryController controller = new CountryController(mock(ICountryService.class));
        EditCountryRequest request = request();
        request.setAddressOrder(AddressOrder.FORWARD);

        Country country = controller.convertRequest(null, request);

        assertThat(country.getCountryMeta().getAddressOrder())
                .isEqualTo(AddressOrder.FORWARD);
    }

    @Test
    void defaultsOmittedAddressOrderToReverse() {
        CountryController controller = new CountryController(mock(ICountryService.class));
        Country country = controller.convertRequest(null, request());

        assertThat(country.getCountryMeta().getAddressOrder())
                .isEqualTo(AddressOrder.REVERSE);
    }

    private EditCountryRequest request() {
        EditCountryRequest request = new EditCountryRequest();
        request.setName("台湾");
        request.setCode("TW");
        request.setContinentCode("AS");
        request.setCurrencyId("1");
        request.setLanguageIds(List.of("1"));
        request.setFrontServerId("1");
        request.setPhonePrefix("+886");
        request.setAddressFields("province,city,district,postal_code");
        request.setRequiredPhone(false);
        request.setRequiredEmail(false);
        return request;
    }
}
