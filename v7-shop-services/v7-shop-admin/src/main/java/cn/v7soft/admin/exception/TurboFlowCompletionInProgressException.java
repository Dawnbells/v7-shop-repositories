package cn.v7soft.admin.exception;

/**
 * 同一 assignment 的上一份译图还在服务端后处理，这次 complete 是重复上传。
 * <p>
 * 以前重复请求会排在 subTask 锁上等前一份处理完，期间占着请求线程、OSIV 数据库连接
 * 和已解析的几 MB 请求体。现在直接回 409 + reason=COMPLETION_IN_PROGRESS，
 * 插件退避后发心跳即可拿到 COMPLETED，无需再传一次大图。
 */
public class TurboFlowCompletionInProgressException extends RuntimeException {

    /** 与插件约定的信号值。 */
    public static final String REASON = "COMPLETION_IN_PROGRESS";

    public TurboFlowCompletionInProgressException(String assignmentId) {
        super("previous upload of assignment " + assignmentId + " is still being processed");
    }
}
