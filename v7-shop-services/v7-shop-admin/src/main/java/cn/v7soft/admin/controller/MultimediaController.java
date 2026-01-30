package cn.v7soft.admin.controller;

import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.core.enums.ClientResponseEnum;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
@Controller
@RequestMapping("/multimedia")
@Tag(name = "素材中心-多媒体文件展示")
@AllArgsConstructor
public class MultimediaController {
    private final IMultimediaFileService multimediaFileService;

    @GetMapping("/{id}")
    public void show(HttpServletResponse response, @PathVariable(value = "id") String id, @RequestParam(value = "w", required = false, defaultValue = "640") int width) throws ServletException, IOException {
        try(InputStream imageStream = multimediaFileService.download(id, width)) {
            // 使用缓冲流逐步读取并写入数据
            byte[] buffer = new byte[8192]; // 8KB 缓冲区
            int bytesRead;
            OutputStream outStream = response.getOutputStream();
            // 边读取边写入
            while ((bytesRead = imageStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            log.debug("show multimedia error >> " + id + ": ", e);
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("404 Not Found");
        }
    }
}
