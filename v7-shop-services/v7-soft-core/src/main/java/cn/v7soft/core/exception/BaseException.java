package cn.v7soft.core.exception;

import cn.v7soft.core.enums.IResponseEnum;
import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {
    private final IResponseEnum responseEnum;
    private final Object[] args;

    public abstract String getRealCode();

    public BaseException(IResponseEnum responseEnum, Object[] args, String message) {
        super(message);
        this.responseEnum = responseEnum;
        this.args = args;
    }

    public BaseException(IResponseEnum responseEnum, Object[] args, String message, Throwable throwable) {
        super(message, throwable);
        this.responseEnum = responseEnum;
        this.args = args;
    }

    public IResponseEnum getResponseEnum() {
        return responseEnum;
    }

    public Object[] getArgs() {
        return args;
    }

    public int getStatus() {
        return responseEnum.getStatus();
    }
}
