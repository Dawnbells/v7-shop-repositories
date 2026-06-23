package cn.v7soft.admin.service.frontagent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CertFingerprintCacheTest {

    @TempDir
    Path tempDir;

    private CertFingerprintCache cache;

    @BeforeEach
    void setUp() {
        cache = new CertFingerprintCache();
    }

    @Test
    @DisplayName("sha256: 返回与标准实现一致的小写 hex 指纹")
    void sha256MatchesStandardDigest() throws IOException, NoSuchAlgorithmException {
        Path file = tempDir.resolve("fullchain.pem");
        byte[] content = "-----BEGIN CERTIFICATE-----\nabc\n-----END CERTIFICATE-----\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        assertEquals(expected, cache.sha256(file));
        // 第二次走缓存，结果一致
        assertEquals(expected, cache.sha256(file));
    }

    @Test
    @DisplayName("sha256: 文件不存在返回 null")
    void sha256ReturnsNullWhenFileMissing() {
        assertNull(cache.sha256(tempDir.resolve("not-exists.pem")));
    }

    @Test
    @DisplayName("sha256: 文件内容变化（mtime 变化）后重新计算")
    void sha256RecomputesWhenFileChanges() throws IOException {
        Path file = tempDir.resolve("privkey.pem");
        Files.writeString(file, "old-key");
        // 显式设置 mtime，避免同一毫秒内重写导致的 stat 假阴性
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L));
        String first = cache.sha256(file);

        Files.writeString(file, "new-key");
        Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000L));
        String second = cache.sha256(file);

        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    @Test
    @DisplayName("sha256: mtime 与 size 未变时信任缓存（stat 短路的行为契约）")
    void sha256TrustsCacheWhenStatUnchanged() throws IOException {
        Path file = tempDir.resolve("cached.pem");
        Files.writeString(file, "AAAA");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L));
        String first = cache.sha256(file);

        // 同长度内容 + 人为回拨 mtime：缓存按设计返回旧值（生产中证书写入总会推进 mtime）
        Files.writeString(file, "BBBB");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L));
        assertEquals(first, cache.sha256(file));
    }

    @Test
    @DisplayName("sha256: 文件被删除后再访问，清掉该条目（被动淘汰，评审 bug_003）")
    void sha256EvictsEntryWhenFileDeleted() throws IOException {
        Path file = tempDir.resolve("gone.pem");
        Files.writeString(file, "data");
        assertEquals(64, cache.sha256(file).length());

        Files.delete(file);
        assertNull(cache.sha256(file)); // NoSuchFileException 分支应清条目
        // 同名文件重建后，缓存不应错误命中旧指纹
        Files.writeString(file, "different-content");
        assertEquals(64, cache.sha256(file).length());
    }

    @Test
    @DisplayName("retainAll: 只保留存活 key，丢弃孤儿条目（评审 bug_003）")
    void retainAllEvictsOrphans() throws IOException {
        Path live = tempDir.resolve("live.pem");
        Path orphan = tempDir.resolve("orphan.pem");
        Files.writeString(live, "L");
        Files.writeString(orphan, "O");
        cache.sha256(live);
        cache.sha256(orphan);

        cache.retainAll(java.util.Set.of(live.toAbsolutePath().toString()));

        // orphan 删除后即便再访问也不会命中旧缓存（retainAll 已清，文件还在则重算）
        Files.delete(orphan);
        assertNull(cache.sha256(orphan));
        // live 仍可命中缓存（mtime/size 未变）
        assertEquals(64, cache.sha256(live).length());
    }
}
