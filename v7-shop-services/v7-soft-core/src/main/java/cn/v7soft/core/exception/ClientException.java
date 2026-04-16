package cn.v7soft.core.exception;

import cn.v7soft.core.enums.IResponseEnum;

/**
 * 客户端异常
 * 错误来源于用户，比如参数错误，用户安装版本过低，用户支付超时等问题。
 */
public class ClientException extends BaseException {

    public ClientException(IResponseEnum responseEnum, Object[] args, String message) {
        super(responseEnum, args, message);
    }

    public ClientException(IResponseEnum responseEnum, Object[] args, String message, Throwable throwable) {
        super(responseEnum, args, message, throwable);
    }

    @Override
    public String getRealCode() {
        return "C" + getResponseEnum().getCode();
    }
}
