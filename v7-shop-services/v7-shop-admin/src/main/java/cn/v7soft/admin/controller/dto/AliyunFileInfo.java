package cn.v7soft.admin.controller.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AliyunFileInfo {
    private AliyunFileInfoValue FileSize;
    private AliyunFileInfoValue Format;
    private AliyunFileInfoValue FrameCount;
    private AliyunFileInfoValue ImageHeight;
    private AliyunFileInfoValue ImageWidth;
}
