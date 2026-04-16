package cn.v7soft.core.enums;

import cn.v7soft.core.asserts.IRemoteExceptionAssert;
import lombok.Getter;

@Getter
public enum RemoteResponseEnum implements IRemoteExceptionAssert {
    FAILED_COMPANY_IDENTITY(200, "10001", "获取公司信息异常"),
    REMOTE_RESULT_CALL_FAILED(200, "10002", "获取公司信息"),
    REMOTE_CALL_RESULT_FAILED(200, "11001", "远程获取结果失败：%s"),
    REMOTE_CALL_FAILED(200, "11002", "远程调用失败：%s");

    private final int status;
    private final String code;
    private final String message;

    RemoteResponseEnum(int status, String code, String msg) {
        this.status = status;
        this.code = code;
        this.message = msg;
    }

    @Override
    public String getRealCode() {
        return "R" + code;
    }
}
