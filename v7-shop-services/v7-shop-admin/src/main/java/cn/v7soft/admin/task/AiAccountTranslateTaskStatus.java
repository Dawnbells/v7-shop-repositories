package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.TaskState;
import lombok.Getter;

@Getter
public class AiAccountTranslateTaskStatus {

    private final Long taskId;
    private final int totalSubTaskCount;
    private final AtomicInteger processingSubTaskCount = new AtomicInteger(0);
    private final AtomicInteger completedSubTaskCount = new AtomicInteger(0);
    private final AtomicInteger failedSubTaskCount = new AtomicInteger(0);
    private final ConcurrentMap<String, AiAccountTranslateSubTask> subTasks = new ConcurrentHashMap<>();
    private final Product product;
    private final Language language;
    private final Country country;
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
                                        Product product, Language language, Country country, SystemUser owner) {
        this.taskId = taskId;
        this.totalSubTaskCount = totalSubTaskCount;
        this.product = product;
        this.language = language;
        this.country = country;
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

    public void completeSubTask(AiAccountTranslateSubTask subTask) {
        subTask.complete();
        decrementProcessingIfNeeded();
        completedSubTaskCount.incrementAndGet();
        subTasks.put(subTask.getSubTaskId(), subTask);
        refreshProgress();
    }

    public void completeTextSubTask(AiAccountTranslateSubTask subTask, String translatedText) {
        translatedTextMap.put(subTask.getContentKey(), translatedText);
        completeSubTask(subTask);
    }

    public void completeHtmlSubTask(AiAccountTranslateSubTask subTask, String translatedHtml) {
        this.translatedHtml = translatedHtml;
        completeSubTask(subTask);
    }

    public void completeImageSubTask(AiAccountTranslateSubTask subTask, MultimediaFile translatedFile) {
        translatedImageMap.put(subTask.getContent(), translatedFile);
        completeSubTask(subTask);
    }

    public void retrySubTask(AiAccountTranslateSubTask subTask, String message) {
        decrementProcessingIfNeeded();
        subTasks.put(subTask.getSubTaskId(), subTask);
        this.message = message;
        refreshProgress();
    }

    public void failSubTask(AiAccountTranslateSubTask subTask, String message) {
        decrementProcessingIfNeeded();
        failedSubTaskCount.incrementAndGet();
        subTasks.put(subTask.getSubTaskId(), subTask);
        this.message = message;
        refreshProgress();
    }

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

    private void refreshProgress() {
        int finished = completedSubTaskCount.get() + failedSubTaskCount.get();
        this.progress = totalSubTaskCount == 0 ? 100 : Math.min(100, finished * 100 / totalSubTaskCount);
        if (finished >= totalSubTaskCount) {
            if (failedSubTaskCount.get() > 0) {
                this.state = TaskState.FAILED;
                this.message = "AI账号翻译子任务失败: " + failedSubTaskCount.get();
            } else {
                this.state = TaskState.PROCESSING;
                this.message = "AI account translate subtasks complete, assembling product";
            }
        }
        touch();
    }

    public boolean isFinished() {
        return state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED;
    }

    public boolean isReadyToFinalize() {
        int finished = completedSubTaskCount.get() + failedSubTaskCount.get();
        return product != null
                && !isFinished()
                && finished >= totalSubTaskCount
                && failedSubTaskCount.get() == 0
                && !finalized.get();
    }

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
