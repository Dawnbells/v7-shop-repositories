package cn.v7soft.admin.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.dao.enums.NginxConfigType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class NginxConfigWriterTest {

    private static final String SERVER_NAME = "front-a";
    private static final String DOMAIN = "example.com";
    private static final String COMPANY_ID = "1";
    private static final String EXPECTED_PATH = "/www/nginx/" + SERVER_NAME + "/" + DOMAIN + ".conf";

    private MockedStatic<FileUtil> fileUtilMock;
    private Map<String, String> virtualFs;

    @BeforeEach
    void setUp() {
        fileUtilMock = mockStatic(FileUtil.class);
        virtualFs = new LinkedHashMap<>();

        fileUtilMock.when(() -> FileUtil.exist(eq(EXPECTED_PATH)))
                .thenAnswer(inv -> virtualFs.containsKey(EXPECTED_PATH));
        fileUtilMock.when(() -> FileUtil.readUtf8String(eq(EXPECTED_PATH)))
                .thenAnswer(inv -> virtualFs.get(EXPECTED_PATH));
        fileUtilMock.when(() -> FileUtil.writeUtf8String(org.mockito.ArgumentMatchers.anyString(), eq(EXPECTED_PATH)))
                .thenAnswer(inv -> {
                    virtualFs.put(EXPECTED_PATH, inv.getArgument(0));
                    return null;
                });
        fileUtilMock.when(() -> FileUtil.del(eq(EXPECTED_PATH)))
                .thenAnswer(inv -> {
                    virtualFs.remove(EXPECTED_PATH);
                    return true;
                });
    }

    @AfterEach
    void tearDown() {
        fileUtilMock.close();
    }

    @Test
    @DisplayName("writeNginx: 文件不存在 → 写入并返回 true")
    void writeNginxReturnsTrueWhenFileMissing() {
        boolean changed = NginxConfigWriter.writeNginx(SERVER_NAME, DOMAIN, NginxConfigType.THYMELEAF, COMPANY_ID);

        assertTrue(changed);
        assertTrue(virtualFs.containsKey(EXPECTED_PATH));
        fileUtilMock.verify(() -> FileUtil.writeUtf8String(org.mockito.ArgumentMatchers.anyString(), eq(EXPECTED_PATH)), times(1));
    }

    @Test
    @DisplayName("writeNginx: 内容一致 → 不写文件并返回 false")
    void writeNginxReturnsFalseWhenContentUnchanged() {
        boolean firstWrite = NginxConfigWriter.writeNginx(SERVER_NAME, DOMAIN, NginxConfigType.THYMELEAF, COMPANY_ID);
        assertTrue(firstWrite);
        String firstContent = virtualFs.get(EXPECTED_PATH);

        boolean secondWrite = NginxConfigWriter.writeNginx(SERVER_NAME, DOMAIN, NginxConfigType.THYMELEAF, COMPANY_ID);

        assertFalse(secondWrite);
        assertEquals(firstContent, virtualFs.get(EXPECTED_PATH));
        fileUtilMock.verify(() -> FileUtil.writeUtf8String(org.mockito.ArgumentMatchers.anyString(), eq(EXPECTED_PATH)), times(1));
    }

    @Test
    @DisplayName("writeNginx: 内容变化 → 写入并返回 true")
    void writeNginxReturnsTrueWhenContentChanged() {
        NginxConfigWriter.writeNginx(SERVER_NAME, DOMAIN, NginxConfigType.THYMELEAF, COMPANY_ID);

        boolean changed = NginxConfigWriter.writeNginx(SERVER_NAME, DOMAIN, NginxConfigType.NUXT_MALL, COMPANY_ID);

        assertTrue(changed);
        fileUtilMock.verify(() -> FileUtil.writeUtf8String(org.mockito.ArgumentMatchers.anyString(), eq(EXPECTED_PATH)), times(2));
    }

    @Test
    @DisplayName("deleteNginxIfExists: 文件不存在 → 返回 false，不调用 del")
    void deleteNginxIfExistsReturnsFalseWhenFileMissing() {
        boolean deleted = NginxConfigWriter.deleteNginxIfExists(SERVER_NAME, DOMAIN);

        assertFalse(deleted);
        fileUtilMock.verify(() -> FileUtil.del(eq(EXPECTED_PATH)), never());
    }

    @Test
    @DisplayName("deleteNginxIfExists: 文件存在 → 删除并返回 true")
    void deleteNginxIfExistsReturnsTrueWhenFileExists() {
        NginxConfigWriter.writeNginx(SERVER_NAME, DOMAIN, NginxConfigType.THYMELEAF, COMPANY_ID);

        boolean deleted = NginxConfigWriter.deleteNginxIfExists(SERVER_NAME, DOMAIN);

        assertTrue(deleted);
        assertFalse(virtualFs.containsKey(EXPECTED_PATH));
        fileUtilMock.verify(() -> FileUtil.del(eq(EXPECTED_PATH)), times(1));
    }
}
