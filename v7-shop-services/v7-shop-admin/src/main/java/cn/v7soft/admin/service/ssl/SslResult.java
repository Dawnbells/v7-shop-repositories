package cn.v7soft.admin.service.ssl;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class SslResult {
    /**
     * 是否完整执行，false表示超时
     */
    private boolean isCompleted;
    /**
     * 是否执行成功
     */
    private boolean isSuccess;
    /**
     * 是否执行过程中有异常
     */
    private boolean isError;
    /**
     * 执行结果
     */
    private String result;
    /**
     * 执行失败消息
     */
    private String errorMsg;
    /**
     * 失败日志
     */
    private String errLog;
}
