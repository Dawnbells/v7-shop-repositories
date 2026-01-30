package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.address.Address;
import cn.v7soft.dao.entities.primary.Country;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "地址信息响应")
public class AddressResponse extends IdResponse {

    @Schema(title = "省份", example = "北京市")
    private String province;

    @Schema(title = "城市", example = "北京市")
    private String city;

    @Schema(title = "区", example = "朝阳区")
    private String district;

    @Schema(title = "邮政编码", example = "100000")
    private String postalCode;

    @Schema(title = "国家信息")
    private Country country;

    /**
     * 从 Address 实体转换为 AddressResponse
     *
     * @param address 地址实体
     * @return 地址响应
     */
    public static AddressResponse convertEntity(Address address) {
        return AddressResponse.builder()
                .province(address.getProvince())
                .city(address.getCity())
                .district(address.getDistrict())
                .postalCode(address.getPostalCode())
                .build();
    }
}
