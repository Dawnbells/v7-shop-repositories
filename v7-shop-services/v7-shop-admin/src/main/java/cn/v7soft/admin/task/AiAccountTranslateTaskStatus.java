package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.TaskState;
import lombok.Getter;

/**
 * 单个 AsyncTask 的内存运行时状态，跟踪子任务进度和翻译结果。
 * <p>
 * 由 loadTask 创建并存入 runningTasks map，子任务完成时收集翻译产物
 * （translatedTextMap/translatedHtml/translatedImageMap），
 * 所有子任务结束后由 finalizeAiAccountTranslateStatus 组装最终翻译结果。
 * syncTaskStatus 定时器将此状态同步到 DB。
 */
@Getter
public class AiAccountTranslateTaskStatus {

    private final Long taskId;
    private final int totalSubTaskCount;
    private final AtomicInteger processingSubTaskCount = new AtomicInteger(0);
    private final AtomicInteger completedSubTaskCount = new AtomicInteger(0);
    private final AtomicInteger failedSubTaskCount = new AtomicInteger(0);
    private final ConcurrentMap<String, AiAccountTranslateSubTask> subTasks = new ConcurrentHashMap<>();
    private final Long productId;
    private final Language language;
    private final Long countryId;
    private final SystemUser owner;
    private final ConcurrentMap<String, String> translatedTextMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MultimediaFile> translatedImageMap = new ConcurrentHashMap<>();
    private final AtomicBoolean finalizing = new AtomicBoolean(false);
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private volatile String translatedHtml;
    private volatile TaskState state = TaskState.PROCESSING;
    private volatile int progress;
    private volatile String message;
    private volatile LocalDateTime updateTime = LocalDateTime.now();

    public AiAccountTranslateTaskStatus(Long taskId, int totalSubTaskCount,
                                        Long productId, Language language, Long countryId, SystemUser owner) {
        this.taskId = taskId;
        this.totalSubTaskCount = totalSubTaskCount;
        this.productId = productId;
        this.language = language;
        this.countryId = countryId;
        this.owner = owner;
        this.progress = totalSubTaskCount == 0 ? 100 : 0;
        this.message = totalSubTaskCount == 0 ? "没有需要翻译的内容" : null;
    }

    public void addSubTask(AiAccountTranslateSubTask subTask) {
        subTasks.put(subTask.getSubTaskId(), subTask);
        touch();
    }

    public void setProcessing(String message) {
        this.state = TaskState.PROCESSING;
        this.message = message;
        touch();
    }

    public void startSubTask(AiAccountTranslateSubTask subTask) {
        processingSubTaskCount.incrementAndGet();
        subTasks.put(subTask.getSubTaskId(), subTask);
        setProcessing("正在执行AI账号翻译子任务");
    }

    public void dispatchSubTask(AiAccountTranslateSubTask subTask) {
        processingSubTaskCount.incrementAndGet();
        subTasks.put(subTask.getSubTaskId(), subTask);
        setProcessing("TurboFlow image task dispatched");
    }

    /** 子任务执行完成（无特定翻译产物，如 TEXT noop） */
    public void completeSubTask(AiAccountTranslateSubTask subTask) {
        subTask.complete();
        decrementProcessingIfNeeded();
        completedSubTaskCount.incrementAndGet();
        subTasks.put(subTask.getSubTaskId(), subTask);
        refreshProgress(); // 更新进度百分比，检查是否全部完成
    }

    /** 文本翻译完成，收集翻译结果到 translatedTextMap（key=contentHash） */
    public void completeTextSubTask(AiAccountTranslateSubTask subTask, String translatedText) {
        translatedTextMap.put(subTask.getContentKey(), translatedText);
        completeSubTask(subTask);
    }

    /** HTML 翻译完成，存储翻译后的 HTML */
    public void completeHtmlSubTask(AiAccountTranslateSubTask subTask, String translatedHtml) {
        this.translatedHtml = translatedHtml;
        completeSubTask(subTask);
    }

    /** 图片翻译完成，收集翻译后的图片文件到 translatedImageMap（key=imageId） */
    public void completeImageSubTask(AiAccountTranslateSubTask subTask, MultimediaFile translatedFile) {
        translatedImageMap.put(subTask.getContent(), translatedFile);
        completeSubTask(subTask);
    }

    /** 子任务重试，减少处理中计数但不增加完成/失败计数（子任务将重新入队） */
    public void retrySubTask(AiAccountTranslateSubTask subTask, String message) {
        decrementProcessingIfNeeded();
        subTasks.put(subTask.getSubTaskId(), subTask);
        this.message = message;
        refreshProgress();
    }

    /** 子任务执行失败（从 PROCESSING 状态转入） */
    public void failSubTask(AiAccountTranslateSubTask subTask, String message) {
        decrementProcessingIfNeeded();
        failedSubTaskCount.incrementAndGet();
        subTasks.put(subTask.getSubTaskId(), subTask);
        this.message = message;
        refreshProgress();
    }

    /** 子任务在 PENDING 状态直接失败（如账号不可用，未经过 PROCESSING） */
    public void failPendingSubTask(AiAccountTranslateSubTask subTask, String message) {
        failedSubTaskCount.incrementAndGet();
        subTasks.put(subTask.getSubTaskId(), subTask);
        this.message = message;
        refreshProgress();
    }

    public void complete() {
        this.state = TaskState.COMPLETED;
        this.progress = 100;
        this.message = "AI账号翻译任务完成";
        touch();
    }

    public void complete(String message) {
        this.state = TaskState.COMPLETED;
        this.progress = 100;
        this.message = message;
        touch();
    }

    public void fail(String message) {
        this.state = TaskState.FAILED;
        this.progress = 100;
        this.message = message;
        touch();
    }

    private void decrementProcessingIfNeeded() {
        int current;
        do {
            current = processingSubTaskCount.get();
            if (current <= 0) {
                return;
            }
        } while (!processingSubTaskCount.compareAndSet(current, current - 1));
    }

    /** 重新计算进度百分比，全部结束时判断整体成功/失败 */
    private void refreshProgress() {
        int finished = completedSubTaskCount.get() + failedSubTaskCount.get();
        this.progress = totalSubTaskCount == 0 ? 100 : Math.min(100, finished * 100 / totalSubTaskCount);
        if (finished >= totalSubTaskCount) {
            if (completedSubTaskCount.get() == 0 && failedSubTaskCount.get() > 0) {
                this.state = TaskState.FAILED;
                this.message = "所有翻译子任务失败: " + failedSubTaskCount.get();
            } else {
                this.state = TaskState.PROCESSING;
                this.message = failedSubTaskCount.get() > 0
                        ? "部分子任务失败(" + failedSubTaskCount.get() + "), 正在组装已完成的翻译"
                        : "翻译子任务全部完成, 正在组装产物";
            }
        }
        touch();
    }

    /** 是否处于终态（不再需要同步） */
    public boolean isFinished() {
        return state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED;
    }

    /** 是否可以开始组装翻译产物（所有子任务已结束且至少有成功的，尚未开始组装） */
    public boolean isReadyToFinalize() {
        int finished = completedSubTaskCount.get() + failedSubTaskCount.get();
        return productId != null
                && !isFinished()
                && finished >= totalSubTaskCount
                && completedSubTaskCount.get() > 0
                && !finalized.get();
    }

    /** CAS 标记开始组装，防止并发重复组装 */
    public boolean markFinalizing() {
        return finalizing.compareAndSet(false, true);
    }

    public void markFinalized() {
        finalized.set(true);
    }

    private void touch() {
        this.updateTime = LocalDateTime.now();
    }
}
