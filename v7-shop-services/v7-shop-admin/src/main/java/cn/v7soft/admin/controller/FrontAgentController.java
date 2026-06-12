package cn.v7soft.admin.controller;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.frontagent.FrontAgentManifestService;
import cn.v7soft.core.enums.ClientResponseEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 前端机 Go agent 的数据面接口（设计文档 §4.4）。
 * <p>
 * 鉴权与租户由 {@link cn.v7soft.admin.interceptors.FrontAgentInterceptor} 完成：
 * 请求进到这里时，TenantContext 已经是请求 Host 对应的公司，所有查询自动按公司隔离。
 * 本控制器全部为只读 GET 接口——永远不要在这里加写操作。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "前端机 agent 接口")
@RequestMapping("/front-agent")
public class FrontAgentController {

    private final FrontAgentManifestService manifestService;

    /**
     * manifest 清单 +「轮询即回报」：
     * agent 带自身标识与已应用版本轮询；版本一致返回 304（空体，零流量），变化返回全量 manifest。
     * 每次轮询都会刷新该 agent 的回报记录（兼作心跳，reportedAt 停滞即代表 agent 异常）。
     */
    @GetMapping("/manifest")
    @Operation(summary = "前端 agent 拉取域名清单（轮询即回报）")
    public ResponseEntity<String> manifest(@RequestParam("agent") String agent,
                                           @RequestParam(value = "appliedVersion", required = false) String appliedVersion,
                                           @RequestParam(value = "status", required = false) String status,
                                           @RequestParam(value = "message", required = false) String message) {
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(StrUtil.isNotBlank(agent), "agent 标识不能为空");
        manifestService.report(agent, appliedVersion, status, message);
        FrontAgentManifestService.ManifestSnapshot snapshot = manifestService.getManifest();
        if (snapshot.version().equals(appliedVersion)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(snapshot.body());
    }

    @GetMapping("/cert/{domain}/fullchain.pem")
    @Operation(summary = "下载域名证书链")
    public ResponseEntity<byte[]> fullchain(@PathVariable("domain") String domain) throws IOException {
        return serveCertFile(domain, "fullchain.pem");
    }

    @GetMapping("/cert/{domain}/privkey.pem")
    @Operation(summary = "下载域名证书私钥")
    public ResponseEntity<byte[]> privkey(@PathVariable("domain") String domain) throws IOException {
        return serveCertFile(domain, "privkey.pem");
    }

    /**
     * 证书文件流式输出。路径解析含三道安全闸（白名单正则/归属校验/根目录约束），
     * 任何一道不过都按 404 返回，不向调用方泄露具体原因。
     */
    private ResponseEntity<byte[]> serveCertFile(String domain, String fileName) throws IOException {
        Path file = manifestService.resolveCertFile(domain, fileName);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(Files.readAllBytes(file));
    }
}
