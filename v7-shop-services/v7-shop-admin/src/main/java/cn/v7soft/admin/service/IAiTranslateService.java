package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.AiTranslateHtmlRequest;
import cn.v7soft.admin.controller.req.AiTranslateImageRequest;
import cn.v7soft.admin.controller.req.AiTranslateTextRequest;
import cn.v7soft.admin.controller.resp.AiTranslateImageResponse;
import cn.v7soft.dao.entities.primary.SystemUser;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IAiTranslateService {

    void streamText(AiTranslateTextRequest request, SystemUser owner, SseEmitter emitter);

    void streamHtml(AiTranslateHtmlRequest request, SystemUser owner, SseEmitter emitter);

    AiTranslateImageResponse translateImage(AiTranslateImageRequest request, SystemUser owner) throws Exception;
}
