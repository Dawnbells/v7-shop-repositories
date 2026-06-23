package cn.v7soft.admin.service.frontagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 证书文件指纹缓存：以「mtime + size」为失效键的 SHA-256 备忘录。
 * <p>
 * 设计约束（设计文档 §4.4 性能约束）：manifest 每 15s 被各前端机轮询，万级域名下
 * 严禁每次轮询读盘哈希。本缓存把「全量读内容哈希」退化为「全量 stat 元数据」（微秒级），
 * 只有文件真正变化（证书续期/占位证书替换）才重新读取内容计算一次。
 */
@Slf4j
@Component
public class CertFingerprintCache {

    /**
     * 缓存条目：文件修改时间 + 大小一致即视为内容未变
     */
    private record Fingerprint(long lastModifiedMillis, long size, String sha256) {
    }

    private final ConcurrentHashMap<String, Fingerprint> cache = new ConcurrentHashMap<>();

    /**
     * 返回文件内容的 SHA-256（小写 hex）；文件不存在或不可读返回 null。
     */
    public String sha256(Path file) {
        // key 上移到 try 首行：NoSuchFileException 分支也能据此清掉已删文件的残留条目
        String key = file.toAbsolutePath().toString();
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            long mtime = attrs.lastModifiedTime().toMillis();
            Fingerprint hit = cache.get(key);
            if (hit != null && hit.lastModifiedMillis() == mtime && hit.size() == attrs.size()) {
                return hit.sha256();
            }
            byte[] content = Files.readAllBytes(file);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            cache.put(key, new Fingerprint(mtime, attrs.size(), sha));
            return sha;
        } catch (NoSuchFileException e) {
            cache.remove(key); // 文件已删：清掉缓存条目，避免孤儿常驻
            return null;
        } catch (Exception e) {
            log.warn("读取证书指纹失败: {}", file, e);
            return null;
        }
    }

    /**
     * 反向清扫：只保留 liveKeys 中的条目，丢弃其余孤儿。
     * 由 manifest 构建末尾调用（liveKeys = 本轮所有有效域名访问过的证书路径），
     * 覆盖「域名删除后 manifest 不再查询其 path」这类被动分支兜不到的孤儿，
     * 防 ConcurrentHashMap 在万级店铺多年 churn 下单调增长。
     */
    public void retainAll(Set<String> liveKeys) {
        cache.keySet().retainAll(liveKeys);
    }
}
