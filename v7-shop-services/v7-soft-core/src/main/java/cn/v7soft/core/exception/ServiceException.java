package cn.v7soft.core.exception;

import cn.v7soft.core.enums.IResponseEnum;

/**
 * 服务异常
 * 错误来源于当前系统，往往是业务逻辑出错，或程序健壮性差等问题。
 */
public class ServiceException extends BaseException {

    public ServiceException(IResponseEnum responseEnum, Object[] args, String message) {
        super(responseEnum, args, message);
    }

    public ServiceException(IResponseEnum responseEnum, Object[] args, String message, Throwable throwable) {
        super(responseEnum, args, message, throwable);
    }

    @Override
    public String getRealCode() {
        return "S" + getResponseEnum().getCode();
    }
}
