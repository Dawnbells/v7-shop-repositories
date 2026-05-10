package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.AiTranslateHtmlRequest;
import cn.v7soft.admin.controller.req.AiTranslateImageRequest;
import cn.v7soft.admin.controller.req.AiTranslateTextRequest;
import cn.v7soft.admin.controller.resp.AiTranslateImageResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IAiTranslateService {

    void streamText(AiTranslateTextRequest request, SseEmitter emitter);

    void streamHtml(AiTranslateHtmlRequest request, SseEmitter emitter);

    AiTranslateImageResponse translateImage(AiTranslateImageRequest request) throws Exception;
}
