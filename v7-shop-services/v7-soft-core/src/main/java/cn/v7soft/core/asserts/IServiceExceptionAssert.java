package cn.v7soft.core.asserts;

import cn.v7soft.core.exception.BaseException;
import cn.v7soft.core.exception.ServiceException;

public interface IServiceExceptionAssert extends IAsserts {
    @Override
    default BaseException newSpecialException(Object[] args, String message, Throwable throwable) {
        if (throwable == null) {
            return new ServiceException(this, args, message);
        }
        return new ServiceException(this, args, message, throwable);
    }
}
