package cn.v7soft.core.enums;

import cn.v7soft.core.asserts.IClientExceptionAssert;
import lombok.Getter;

@Getter
public enum ClientResponseEnum implements IClientExceptionAssert {
    SUCCESS(200, "0", "成功"),
    PARAMETER_ILLEGAL(200, "0100", "{0}"),
    IMAGE_NOR_FOUND_IN_DB(404, "0101", "{0}"),
    IMAGE_NOR_FOUND_IN_OSS(404, "0102", "{0}"),
    REGISTER_DUPLICATE_TELEPHONE(200, "0112", "当前手机号已绑定其他账号"),
    PRODUCT_NOT_FOUND(404, "0113", "商品不存在"),
    NOT_FOUND(404, "0114",  "{0}"),
    PRODUCT_LANGUAGE_NOT_FOUND(404, "0114", "商品未设置该语言: {0}"),
    WEBSITE_LANGUAGE_NOT_SUPPORT(404, "0115", "商城不支持该语言: {0}"),
    LOGIN_FAILED(200, "0200", "登录失败, 账号或密码错!"),
    NO_PERMISSION(200, "0201", "{0}"),
    PERMISSION_DENIED(200, "0202", "{0}"),
    ;

    private final int status;
    private final String code;
    private final String message;

    ClientResponseEnum(int status, String code, String msg) {
        this.status = status;
        this.code = code;
        this.message = msg;
    }

    @Override
    public String getRealCode() {
        return "C" + code;
    }

}
