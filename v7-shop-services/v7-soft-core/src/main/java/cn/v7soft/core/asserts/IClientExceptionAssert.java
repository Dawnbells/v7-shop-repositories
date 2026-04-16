package cn.v7soft.core.asserts;


import cn.v7soft.core.exception.BaseException;
import cn.v7soft.core.exception.ClientException;

public interface IClientExceptionAssert extends IAsserts {
    @Override
    default BaseException newSpecialException(Object[] args, String message, Throwable throwable) {
        if (throwable == null) {
            return new ClientException(this, args, message);
        }
        return new ClientException(this, args, message, throwable);
    }
}
