package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.ViewMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 异步任务实体类，表示系统中的异步任务。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_async_tasks")
@SQLRestriction("status <> 'DELETED'")
public class AsyncTask extends BaseDataRangeEntity {

    /**
     * 任务类型（如订单下载、内容下载等）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    /**
     * 任务状态（如 PENDING, RUNNING, COMPLETED, FAILED）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private TaskState state;

    /**
     * 任务进度（0-100）
     */
    @Column(name = "progress", nullable = false)
    private Integer progress;

    /**
     * 使用 MySQL JSON 数据类型存储任务参数
     */
    @Column(name = "parameters", columnDefinition = "JSON", nullable = false)
    private String parameters;

    /**
     * 导出文件的相对路径
     */
    @Column(name = "export_relative_path")
    private String exportRelativePath;

    /**
     * 上传文件路径
     */
    @Column(name = "upload_file_path")
    private String uploadFilePath;

    /**
     * 结果说明或错误消息
     */
    @Column(name = "message", columnDefinition = "MEDIUMTEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_mode")
    private ViewMode viewMode;

    /**
     * 任务展示名称
     */
    @Column(name = "name", length = 255)
    private String name;

    /**
     * 幂等去重键，格式：{taskType}:{businessKey}
     * 用于替代 parameters JSON 字符串全等比较
     */
    @Column(name = "dedup_key", length = 255)
    private String dedupKey;

    @Column(name = "batch_job_name")
    private String batchJobName;

    /**
     * 用户是否已确认/已读此任务结果
     */
    @lombok.Builder.Default
    @Column(name = "acknowledged", nullable = false)
    private Boolean acknowledged = false;
}
