package cn.v7soft.core.exception;


import cn.v7soft.core.enums.IResponseEnum;

/**
 * 远程服务调用异常
 * 错误来源于第三方服务，比如 CDN 服务出错，消息投递超时等问题。
 */
public class RemoteException extends BaseException {
    public RemoteException(IResponseEnum responseEnum, Object[] args, String message) {
        super(responseEnum, args, message);
    }

    public RemoteException(IResponseEnum responseEnum, Object[] args, String message, Throwable throwable) {
        super(responseEnum, args, message, throwable);
    }
    @Override
    public String getRealCode() {
        return "R" + getResponseEnum().getCode();
    }
}
