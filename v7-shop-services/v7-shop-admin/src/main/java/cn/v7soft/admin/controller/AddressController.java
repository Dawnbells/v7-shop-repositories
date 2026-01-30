package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.req.EditAddressRequest;
import cn.v7soft.admin.controller.req.QueryAddressRequest;
import cn.v7soft.admin.controller.resp.AddressResponse;
import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.dao.entities.address.Address;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Validated
@RestController
@RequestMapping("/address")
@Tag(name = "地址管理")
public class AddressController extends BaseController<Address, IAddressService, AddressResponse, QueryAddressRequest, EditAddressRequest> {

    protected AddressController(IAddressService service) {
        super(service);
    }

    @Override
    protected AddressResponse convertEntity(Address address) {
        return AddressResponse.convertEntity(address);
    }

    @Override
    protected Address convertRequest(@Nullable Address dbEntity, EditAddressRequest request) {
        Address address = Optional.ofNullable(dbEntity).orElse(Address.builder().build());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setPostalCode(request.getPostalCode());
        return address;
    }

    @Override
    protected String getPermissionPrefix() {
        return "address";
    }
}

