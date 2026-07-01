package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.SystemUser;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 翻译子任务领域对象。
 * <p>
 * 状态转移：PENDING → PROCESSING → COMPLETED / FAILED
 * <p>
 * 对于 TurboFlow 任务，dispatch() 设置 assignmentId + leaseUntil 后进入 PROCESSING。
 * lease 过期或插件报告失败时，通过 retry() 重置回 PENDING 等待重试。
 * resetAttemptCount() 在过期超过最大重试次数时调用，允许任务重新排队到待执行队尾。
 */
@Getter
public class AiAccountTranslateSubTask {

    private final String subTaskId;
    private final Long taskId;
    private final AiAccountTranslateSubTaskType type;
    private final String contentKey;
    private final String content;
    private final String productId;
    private final String countryId;
    private final String languageId;
    private final Long aiAccountId;
    @Setter
    private volatile SystemUser owner;
    @Setter
    private volatile String imageHash;
    /** 子任务被跳过翻译（如动图），dispatch 前直接完成，不扣费、不冻结积分、不调用 Provider */
    @Setter
    private volatile boolean skipped;
    @Setter
    private volatile String skipReason;
    private volatile String assignmentId;
    private volatile String assignedBridgeId;
    private volatile LocalDateTime leaseUntil;
    private final AtomicInteger attemptCount = new AtomicInteger(0);
    private final AtomicBoolean priorityRetry = new AtomicBoolean(false);
    private volatile AiAccountTranslateSubTaskState state = AiAccountTranslateSubTaskState.PENDING;
    private volatile String message;

    public AiAccountTranslateSubTask(Long taskId, AiAccountTranslateSubTaskType type, String contentKey,
                                     String content, TranslateByAIRequest request) {
        this.taskId = taskId;
        this.type = type;
        this.contentKey = contentKey;
        this.content = content;
        this.productId = request.getProductId();
        this.countryId = request.getCountryId();
        this.languageId = request.getLanguageId();
        if (StrUtil.isBlank(request.getAiAccountId())) {
            throw new IllegalArgumentException("AI账号不能为空");
        }
        this.aiAccountId = Long.parseLong(request.getAiAccountId());
        this.subTaskId = taskId + ":" + type + ":" + contentKey;
    }

    public static AiAccountTranslateSubTask text(Long taskId, String hash, String text, TranslateByAIRequest request) {
        return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.TEXT, hash, text, request);
    }

    public static AiAccountTranslateSubTask html(Long taskId, String hash, String html, TranslateByAIRequest request) {
        return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.HTML, hash, html, request);
    }

    public static AiAccountTranslateSubTask image(Long taskId, String imageId, TranslateByAIRequest request) {
        return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.IMAGE, imageId, imageId, request);
    }

    public void start() {
        this.state = AiAccountTranslateSubTaskState.PROCESSING;
        this.message = null;
        this.priorityRetry.set(false);
    }

    public void dispatch(String bridgeId, String assignmentId, LocalDateTime leaseUntil) {
        this.assignmentId = assignmentId;
        this.assignedBridgeId = bridgeId;
        this.leaseUntil = leaseUntil;
        this.state = AiAccountTranslateSubTaskState.PROCESSING;
        this.message = null;
        this.priorityRetry.set(false);
        this.attemptCount.incrementAndGet();
    }

    public boolean isAssignedTo(String bridgeId, String assignmentId) {
        return assignmentId != null
                && assignmentId.equals(this.assignmentId)
                && (StrUtil.isBlank(bridgeId) || bridgeId.equals(this.assignedBridgeId));
    }

    /**
     * 插件端重启后 bridgeId 可能变化；在已校验 assignmentId + token 账号归属后，将分配绑定到当前 bridge，
     * 以便后续 complete/fail 的 isAssignedTo 校验通过。
     */
    public void rebindAssignedBridge(String bridgeId) {
        if (StrUtil.isNotBlank(bridgeId)) {
            this.assignedBridgeId = bridgeId;
        }
    }

    public boolean isLeaseExpired(LocalDateTime now) {
        return state == AiAccountTranslateSubTaskState.PROCESSING
                && leaseUntil != null
                && now.isAfter(leaseUntil);
    }

    public void retry(String message) {
        this.state = AiAccountTranslateSubTaskState.PENDING;
        this.message = message;
        this.assignmentId = null;
        this.assignedBridgeId = null;
        this.leaseUntil = null;
        this.priorityRetry.set(true);
    }

    public void complete() {
        this.state = AiAccountTranslateSubTaskState.COMPLETED;
        this.message = null;
        this.assignmentId = null;
        this.assignedBridgeId = null;
        this.leaseUntil = null;
        this.priorityRetry.set(false);
    }

    public void fail(String message) {
        this.state = AiAccountTranslateSubTaskState.FAILED;
        this.message = message;
        this.assignmentId = null;
        this.assignedBridgeId = null;
        this.leaseUntil = null;
        this.priorityRetry.set(false);
    }

    public void resetAttemptCount() {
        this.attemptCount.set(0);
    }

    public boolean consumePriorityRetry() {
        return this.priorityRetry.getAndSet(false);
    }

    public MultimediaFile resolveSourceFile(IMultimediaFileService multimediaFileService) {
        if (type != AiAccountTranslateSubTaskType.IMAGE) {
            throw new IllegalStateException("not an image task");
        }
        return multimediaFileService.getById(Long.parseLong(content));
    }
}
