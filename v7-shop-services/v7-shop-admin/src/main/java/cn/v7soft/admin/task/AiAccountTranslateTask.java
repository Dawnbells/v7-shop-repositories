package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAccountTranslateTask {

    private static final int MAX_TASKS_PER_ROUND = 10;
    private static final int SUBTASK_EXECUTE_BATCH_SIZE = 10;
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private final AsyncTaskRepository asyncTaskRepository;
    private final IProductService productService;
    private final ConcurrentLinkedDeque<AiAccountTranslateSubTask> subTaskStack = new ConcurrentLinkedDeque<>();
    private final ConcurrentMap<Long, AiAccountTranslateTaskStatus> runningTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean loadingTasks = new AtomicBoolean(false);
    private final AtomicBoolean executingSubTasks = new AtomicBoolean(false);
    private final AtomicBoolean syncingTaskStatus = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 30 * 1000)
    public void executePendingTasks() {
        if (!loadingTasks.compareAndSet(false, true)) {
            return;
        }
        try {
            List<AsyncTask> tasks = asyncTaskRepository.findByTaskTypeAndStateOrderByCreateTimeAsc(
                    TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE,
                    TaskState.PENDING,
                    PageRequest.of(0, MAX_TASKS_PER_ROUND));

            for (AsyncTask task : tasks) {
                if (runningTasks.containsKey(task.getId())) {
                    continue;
                }
                loadTask(task);
            }
        } finally {
            loadingTasks.set(false);
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 35 * 1000)
    public void executeSubTasks() {
        if (!executingSubTasks.compareAndSet(false, true)) {
            return;
        }
        try {
            for (int i = 0; i < SUBTASK_EXECUTE_BATCH_SIZE; i++) {
                AiAccountTranslateSubTask subTask = subTaskStack.poll();
                if (subTask == null) {
                    return;
                }
                executeSubTask(subTask);
            }
        } finally {
            executingSubTasks.set(false);
        }
    }

    @Scheduled(fixedDelay = 5 * 1000, initialDelay = 40 * 1000)
    public void syncTaskStatus() {
        if (!syncingTaskStatus.compareAndSet(false, true)) {
            return;
        }
        try {
            for (AiAccountTranslateTaskStatus status : runningTasks.values()) {
                syncSingleTaskStatus(status);
            }
        } finally {
            syncingTaskStatus.set(false);
        }
    }

    private void loadTask(AsyncTask task) {
        try {
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            List<AiAccountTranslateSubTask> subTasks = buildSubTasks(task.getId(), request);
            AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(task.getId(), subTasks.size());
            AiAccountTranslateTaskStatus existing = runningTasks.putIfAbsent(task.getId(), status);
            if (existing != null) {
                return;
            }

            if (subTasks.isEmpty()) {
                status.complete();
                return;
            }

            for (AiAccountTranslateSubTask subTask : subTasks) {
                status.addSubTask(subTask);
                subTaskStack.push(subTask);
            }
            status.setProcessing("已拆分AI账号翻译子任务: " + subTasks.size());
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] 拆分任务失败: taskId={}", task.getId(), e);
            AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(task.getId(), 0);
            status.fail("拆分任务失败: " + e.getMessage());
            runningTasks.put(task.getId(), status);
        }
    }

    private List<AiAccountTranslateSubTask> buildSubTasks(Long taskId, TranslateByAIRequest request) {
        Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
        List<AiAccountTranslateSubTask> subTasks = new ArrayList<>();

        collectTextsToTranslate(product).forEach((hash, text) ->
                subTasks.add(AiAccountTranslateSubTask.text(taskId, hash, text, request)));

        if (product.getIntroduction() != null && !product.getIntroduction().isBlank()) {
            String hash = DigestUtil.sha256Hex(product.getIntroduction());
            subTasks.add(AiAccountTranslateSubTask.html(taskId, hash, product.getIntroduction(), request));
        }

        for (String imageId : collectImageIds(product)) {
            subTasks.add(AiAccountTranslateSubTask.image(taskId, imageId, request));
        }

        return subTasks;
    }

    private Map<String, String> collectTextsToTranslate(Product product) {
        Map<String, String> textMap = new LinkedHashMap<>();
        addTextIfPresent(textMap, product.getTitle());
        addTextIfPresent(textMap, product.getSummary());
        addTextIfPresent(textMap, product.getWaybillProductName());
        if (product.getSpecificationList() != null) {
            for (ProductSpecification spec : product.getSpecificationList()) {
                if (spec.getAttributes() == null) {
                    continue;
                }
                for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                    addTextIfPresent(textMap, attr.getName());
                    addTextIfPresent(textMap, attr.getValue());
                }
            }
        }
        return textMap;
    }

    private void addTextIfPresent(Map<String, String> textMap, String text) {
        if (text != null && !text.isBlank()) {
            textMap.putIfAbsent(DigestUtil.sha256Hex(text), text);
        }
    }

    private List<String> collectImageIds(Product product) {
        Set<String> imageIds = new LinkedHashSet<>();
        if (product.getImageFiles() != null) {
            for (MultimediaFile image : product.getImageFiles()) {
                addImageIdIfPresent(imageIds, image);
            }
        }
        if (product.getSpecificationList() != null) {
            for (ProductSpecification spec : product.getSpecificationList()) {
                addImageIdIfPresent(imageIds, spec.getSpecificationImage());
                if (spec.getAttributes() == null) {
                    continue;
                }
                for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                    addImageIdIfPresent(imageIds, attr.getMultimediaFile());
                }
            }
        }
        if (product.getIntroduction() != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(product.getIntroduction());
            while (matcher.find()) {
                imageIds.add(matcher.group(1));
            }
        }
        return new ArrayList<>(imageIds);
    }

    private void addImageIdIfPresent(Set<String> imageIds, MultimediaFile image) {
        if (image != null && image.getId() != null && !"gif".equalsIgnoreCase(image.getSuffix())) {
            imageIds.add(String.valueOf(image.getId()));
        }
    }

    private void executeSubTask(AiAccountTranslateSubTask subTask) {
        AiAccountTranslateTaskStatus taskStatus = runningTasks.get(subTask.getTaskId());
        if (taskStatus == null) {
            return;
        }

        taskStatus.startSubTask(subTask);
        try {
            subTask.start();
            // TODO 接入新的 AI 账号翻译执行流程。
            subTask.complete();
            taskStatus.completeSubTask(subTask);
        } catch (Exception e) {
            subTask.fail(e.getMessage());
            taskStatus.failSubTask(subTask, e.getMessage());
            log.error("[AiAccountTranslateTask] 子任务执行失败: taskId={}, subTaskId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), e);
        }
    }

    private void syncSingleTaskStatus(AiAccountTranslateTaskStatus status) {
        asyncTaskRepository.findById(status.getTaskId()).ifPresent(task -> {
            if (task.getState() == TaskState.CANCELLED) {
                runningTasks.remove(status.getTaskId());
                return;
            }

            task.setState(status.getState());
            task.setProgress(status.getProgress());
            task.setMessage(status.getMessage());
            asyncTaskRepository.save(task);

            if (status.isFinished()) {
                runningTasks.remove(status.getTaskId());
            }
        });
    }

    private enum AiAccountTranslateSubTaskType {
        TEXT, HTML, IMAGE
    }

    private enum AiAccountTranslateSubTaskState {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Getter
    private static class AiAccountTranslateSubTask {

        private final String subTaskId;
        private final Long taskId;
        private final AiAccountTranslateSubTaskType type;
        private final String contentKey;
        private final String content;
        private final String productId;
        private final String countryId;
        private final String languageId;
        private final String aiAccountId;
        private volatile AiAccountTranslateSubTaskState state = AiAccountTranslateSubTaskState.PENDING;
        private volatile String message;

        private AiAccountTranslateSubTask(Long taskId, AiAccountTranslateSubTaskType type, String contentKey,
                                          String content, TranslateByAIRequest request) {
            this.taskId = taskId;
            this.type = type;
            this.contentKey = contentKey;
            this.content = content;
            this.productId = request.getProductId();
            this.countryId = request.getCountryId();
            this.languageId = request.getLanguageId();
            this.aiAccountId = request.getAiAccountId();
            this.subTaskId = taskId + ":" + type + ":" + contentKey;
        }

        private static AiAccountTranslateSubTask text(Long taskId, String hash, String text, TranslateByAIRequest request) {
            return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.TEXT, hash, text, request);
        }

        private static AiAccountTranslateSubTask html(Long taskId, String hash, String html, TranslateByAIRequest request) {
            return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.HTML, hash, html, request);
        }

        private static AiAccountTranslateSubTask image(Long taskId, String imageId, TranslateByAIRequest request) {
            return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.IMAGE, imageId, imageId, request);
        }

        private void start() {
            this.state = AiAccountTranslateSubTaskState.PROCESSING;
            this.message = null;
        }

        private void complete() {
            this.state = AiAccountTranslateSubTaskState.COMPLETED;
            this.message = null;
        }

        private void fail(String message) {
            this.state = AiAccountTranslateSubTaskState.FAILED;
            this.message = message;
        }
    }

    @Getter
    private static class AiAccountTranslateTaskStatus {

        private final Long taskId;
        private final int totalSubTaskCount;
        private final AtomicInteger processingSubTaskCount = new AtomicInteger(0);
        private final AtomicInteger completedSubTaskCount = new AtomicInteger(0);
        private final AtomicInteger failedSubTaskCount = new AtomicInteger(0);
        private final ConcurrentMap<String, AiAccountTranslateSubTask> subTasks = new ConcurrentHashMap<>();
        private volatile TaskState state = TaskState.PROCESSING;
        private volatile int progress;
        private volatile String message;
        private volatile LocalDateTime updateTime = LocalDateTime.now();

        private AiAccountTranslateTaskStatus(Long taskId, int totalSubTaskCount) {
            this.taskId = taskId;
            this.totalSubTaskCount = totalSubTaskCount;
            this.progress = totalSubTaskCount == 0 ? 100 : 0;
            this.message = totalSubTaskCount == 0 ? "没有需要翻译的内容" : null;
        }

        private void addSubTask(AiAccountTranslateSubTask subTask) {
            subTasks.put(subTask.getSubTaskId(), subTask);
            touch();
        }

        private void setProcessing(String message) {
            this.state = TaskState.PROCESSING;
            this.message = message;
            touch();
        }

        private void startSubTask(AiAccountTranslateSubTask subTask) {
            processingSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            setProcessing("正在执行AI账号翻译子任务");
        }

        private void completeSubTask(AiAccountTranslateSubTask subTask) {
            processingSubTaskCount.decrementAndGet();
            completedSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            refreshProgress();
        }

        private void failSubTask(AiAccountTranslateSubTask subTask, String message) {
            processingSubTaskCount.decrementAndGet();
            failedSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            this.message = message;
            refreshProgress();
        }

        private void complete() {
            this.state = TaskState.COMPLETED;
            this.progress = 100;
            this.message = "AI账号翻译任务完成";
            touch();
        }

        private void fail(String message) {
            this.state = TaskState.FAILED;
            this.progress = 100;
            this.message = message;
            touch();
        }

        private void refreshProgress() {
            int finished = completedSubTaskCount.get() + failedSubTaskCount.get();
            this.progress = totalSubTaskCount == 0 ? 100 : Math.min(100, finished * 100 / totalSubTaskCount);
            if (finished >= totalSubTaskCount) {
                if (failedSubTaskCount.get() > 0) {
                    this.state = TaskState.FAILED;
                    this.message = "AI账号翻译子任务失败: " + failedSubTaskCount.get();
                } else {
                    complete();
                    return;
                }
            }
            touch();
        }

        private boolean isFinished() {
            return state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED;
        }

        private void touch() {
            this.updateTime = LocalDateTime.now();
        }
    }
}
