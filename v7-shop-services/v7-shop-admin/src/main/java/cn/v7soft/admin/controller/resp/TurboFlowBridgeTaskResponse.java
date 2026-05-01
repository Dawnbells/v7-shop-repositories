package cn.v7soft.admin.controller.resp;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TurboFlowBridgeTaskResponse {

    private boolean hasTask;
    private String message;
    private Long taskId;
    private String subTaskId;
    private String assignmentId;
    private String imageBase64;
    private String fileName;
    private String mimeType;
    private String targetLanguage;
    private String targetLanguageCode;
    private Integer sourceWidth;
    private Integer sourceHeight;
    private LocalDateTime leaseUntil;
}
