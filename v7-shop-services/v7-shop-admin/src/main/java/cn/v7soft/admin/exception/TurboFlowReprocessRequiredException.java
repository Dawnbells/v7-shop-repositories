package cn.v7soft.admin.exception;

/**
 * 插件送来的译图已收到，但服务端后处理（解码 / 存图 / 写缓存）失败。
 * <p>
 * 抛出前 assignment 已被移除、子任务已重排，所以插件再用同一个 assignmentId 重投必然
 * 收到 "assignment not found or expired"。为免插件白跑三次指数退避，controller 把这个
 * 异常映射成 HTTP 200 + accepted=false + reason=REPROCESS_REQUIRED，
 * 让插件直接把译图落盘、等服务端用新 assignmentId 重派后按 sha256 复用。
 */
public class TurboFlowReprocessRequiredException extends RuntimeException {

    /** 与插件约定的信号值，插件端见 REPROCESS_REQUIRED_REASON。 */
    public static final String REASON = "REPROCESS_REQUIRED";

    public TurboFlowReprocessRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
