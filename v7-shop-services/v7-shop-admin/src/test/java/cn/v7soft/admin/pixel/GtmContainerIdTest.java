package cn.v7soft.admin.pixel;

import cn.v7soft.dao.enums.PixelAccountPlatform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link PixelAccountPlatform#normalizeGtmContainerId(String)} 单元测试。
 *
 * <p>重点验证：合法容器 ID 被规范化为 {@code GTM-XXXXXX}，
 * 而带有脚本/属性注入意图的恶意 pixelId 一律被拒绝（返回 null），
 * 从而不会进入前端注入的 inline script / iframe URL。
 */
class GtmContainerIdTest {

    @Test
    @DisplayName("合法容器 ID 原样规范化")
    void acceptsValidContainerId() {
        assertEquals("GTM-ABC123", PixelAccountPlatform.normalizeGtmContainerId("GTM-ABC123"));
    }

    @Test
    @DisplayName("裸 ID 自动补 GTM- 前缀")
    void prefixesBareId() {
        assertEquals("GTM-ABC123", PixelAccountPlatform.normalizeGtmContainerId("ABC123"));
    }

    @Test
    @DisplayName("小写统一为大写，并去除首尾空白")
    void normalizesCaseAndTrims() {
        assertEquals("GTM-ABC123", PixelAccountPlatform.normalizeGtmContainerId("  gtm-abc123  "));
    }

    @Test
    @DisplayName("空值与空白被拒绝")
    void rejectsBlank() {
        assertNull(PixelAccountPlatform.normalizeGtmContainerId(null));
        assertNull(PixelAccountPlatform.normalizeGtmContainerId(""));
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("   "));
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("GTM-"));
    }

    @Test
    @DisplayName("脚本/属性注入型 pixelId 一律被拒绝")
    void rejectsInjectionPayloads() {
        // 试图截断 inline script 字符串并执行任意代码
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("GTM-ABC');alert(1)//"));
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("GTM-ABC\");alert(1)//"));
        // 试图闭合 <script> / 注入新标签
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("GTM-ABC</script><script>alert(1)</script>"));
        // 试图闭合 iframe src 属性
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("GTM-ABC\"></iframe><img src=x onerror=alert(1)>"));
        // 含空格 / 特殊字符
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("GTM-AB C"));
        assertNull(PixelAccountPlatform.normalizeGtmContainerId("GTM-ABC?id=evil"));
    }
}
