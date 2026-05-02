package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.SystemUser;
import lombok.Getter;

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
    private volatile SystemUser owner;
    private volatile String imageHash;
    private volatile String assignmentId;
    private volatile String assignedBridgeId;
    private volatile LocalDateTime leaseUntil;
    private final AtomicInteger attemptCount = new AtomicInteger(0);
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
    }

    public void dispatch(String bridgeId, String assignmentId, LocalDateTime leaseUntil) {
        this.assignmentId = assignmentId;
        this.assignedBridgeId = bridgeId;
        this.leaseUntil = leaseUntil;
        this.state = AiAccountTranslateSubTaskState.PROCESSING;
        this.message = null;
        this.attemptCount.incrementAndGet();
    }

    public boolean isAssignedTo(String bridgeId, String assignmentId) {
        return assignmentId != null
                && assignmentId.equals(this.assignmentId)
                && (StrUtil.isBlank(bridgeId) || bridgeId.equals(this.assignedBridgeId));
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
    }

    public void complete() {
        this.state = AiAccountTranslateSubTaskState.COMPLETED;
        this.message = null;
        this.assignmentId = null;
        this.assignedBridgeId = null;
        this.leaseUntil = null;
    }

    public void fail(String message) {
        this.state = AiAccountTranslateSubTaskState.FAILED;
        this.message = message;
        this.assignmentId = null;
        this.assignedBridgeId = null;
        this.leaseUntil = null;
    }

    public void resetAttemptCount() {
        this.attemptCount.set(0);
    }

    public void setOwner(SystemUser owner) {
        this.owner = owner;
    }

    public void setImageHash(String imageHash) {
        this.imageHash = imageHash;
    }

    public MultimediaFile resolveSourceFile(IMultimediaFileService multimediaFileService) {
        if (type != AiAccountTranslateSubTaskType.IMAGE) {
            throw new IllegalStateException("not an image task");
        }
        return multimediaFileService.getById(Long.parseLong(content));
    }
}
