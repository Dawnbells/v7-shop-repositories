package cn.v7soft.core.asserts;

import cn.v7soft.core.exception.BaseException;
import cn.v7soft.core.exception.RemoteException;

public interface IRemoteExceptionAssert extends IAsserts {
    @Override
    default BaseException newSpecialException(Object[] args, String message, Throwable throwable) {
        if (throwable == null) {
            return new RemoteException(this, args, message);
        }
        return new RemoteException(this, args, message, throwable);
    }
}
