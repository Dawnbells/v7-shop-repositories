package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "配送信息请求实体类")
public class TemporaryOrderDeliveryInfoRequest {

    @Schema(description = "电子邮件")
    private String email;

    @Schema(description = "是否接收更新")
    private boolean receiveUpdates;

    @Schema(description = "名字")
    private String firstName;

    @Schema(description = "姓氏")
    private String lastName;

    @Schema(description = "电话号码")
    private String phone;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "区县")
    private String district;

    @Schema(description = "邮政编码")
    private String postalCode;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "是否为远程区域")
    private boolean remoteArea;

    @Schema(description = "备注")
    private String remark;
}
