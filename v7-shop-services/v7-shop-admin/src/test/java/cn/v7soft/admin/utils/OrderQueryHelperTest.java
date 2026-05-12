package cn.v7soft.admin.utils;

import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderQueryHelper 单元测试。
 *
 * <p>核心验证 "按下单域名查询" 的匹配语义：
 * <ul>
 *   <li>websiteUrl = keyword</li>
 *   <li>OR websiteUrl LIKE '%.${keyword}'</li>
 * </ul>
 *
 * <p>由 v7-shop-mall 写入：{@code websiteUrl = pageContext.subDomain.fullName}，
 * 因此 websiteUrl 字段存储的是纯 host（如 "de.varlar.com"），不带协议头和路径。
 */
class OrderQueryHelperTest {

    @Test
    @DisplayName("normalizeDomainKeyword: 输入纯 host → 原样返回")
    void normalizeKeywordKeepsPlainHost() {
        assertEquals("varlar.com", OrderQueryHelper.normalizeDomainKeyword("varlar.com"));
        assertEquals("de.varlar.com", OrderQueryHelper.normalizeDomainKeyword("de.varlar.com"));
    }

    @Test
    @DisplayName("normalizeDomainKeyword: 带协议头 → 剥离协议返回 host")
    void normalizeKeywordStripsScheme() {
        assertEquals("varlar.com", OrderQueryHelper.normalizeDomainKeyword("https://varlar.com"));
        assertEquals("de.varlar.com", OrderQueryHelper.normalizeDomainKeyword("http://de.varlar.com"));
    }

    @Test
    @DisplayName("normalizeDomainKeyword: 带路径/查询 → 仅保留 host")
    void normalizeKeywordStripsPath() {
        assertEquals("de.varlar.com", OrderQueryHelper.normalizeDomainKeyword("https://de.varlar.com/abc"));
        assertEquals("de.varlar.com", OrderQueryHelper.normalizeDomainKeyword("de.varlar.com/abc?x=1"));
    }

    @Test
    @DisplayName("normalizeDomainKeyword: 空白输入 → 返回空串")
    void normalizeKeywordHandlesBlank() {
        assertEquals("", OrderQueryHelper.normalizeDomainKeyword(""));
        assertEquals("", OrderQueryHelper.normalizeDomainKeyword("   "));
    }

    @Test
    @DisplayName("buildHostMatchAttribute: 生成的 Predicate = (websiteUrl = keyword) OR (websiteUrl LIKE '%.keyword')")
    void buildHostMatchAttributeConstructsCorrectPredicate() {
        String keyword = "varlar.com";
        HostMatchHarness harness = new HostMatchHarness(keyword);

        harness.run();

        // 核心断言：精确相等 + 按 . 边界 LIKE 各调用一次
        verify(harness.cb, times(1)).equal(harness.websiteUrlPath, keyword);
        verify(harness.cb, times(1)).like(harness.websiteUrlPath, "%." + keyword);
        // 上述两个 Predicate 被 OR 组合
        verify(harness.cb, times(1)).or(any(Predicate.class), any(Predicate.class));
    }

    @Test
    @DisplayName("buildHostMatchAttribute: 子域名 keyword 不会误匹配 ide.varlar.com 这种含子串的 host")
    void buildHostMatchAttributeIsExactForSpecificSubdomain() {
        // 这个测试验证 LIKE 模式的字面量为 "%.${keyword}"，而不是 "%${keyword}%"
        // 因此 keyword="de.varlar.com" 生成的 LIKE 模式是 "%.de.varlar.com"
        // 它不会匹配 "ide.varlar.com"（不以 ".de.varlar.com" 结尾）
        String keyword = "de.varlar.com";
        HostMatchHarness harness = new HostMatchHarness(keyword);

        harness.run();

        verify(harness.cb).like(harness.websiteUrlPath, "%.de.varlar.com");
        verify(harness.cb).equal(harness.websiteUrlPath, "de.varlar.com");
    }

    /**
     * 用 Mockito 构造 JPA Criteria API 的最小测试桩，
     * 让 {@link OrderQueryHelper#buildHostMatchAttribute(String)} 产生的 QueryAttribute
     * 可以执行 toPredicate(...) 而不抛 NPE，从而可对 CriteriaBuilder 做调用验证。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class HostMatchHarness {
        final CriteriaBuilder cb = mock(CriteriaBuilder.class);
        final Root root = mock(Root.class);
        final CriteriaQuery query = mock(CriteriaQuery.class);
        final Path contextInfoPath = mock(Path.class);
        final Path<String> websiteUrlPath = mock(Path.class);
        final QueryAttribute attribute;

        HostMatchHarness(String keyword) {
            when(root.get("contextInfo")).thenReturn(contextInfoPath);
            when(contextInfoPath.get("websiteUrl")).thenReturn(websiteUrlPath);

            Predicate equalPredicate = mock(Predicate.class);
            Predicate likePredicate = mock(Predicate.class);
            Predicate orPredicate = mock(Predicate.class);
            // 用 any(Object.class) 才能匹配 CriteriaBuilder#equal(Expression, Object) 重载（实际传入 String）
            when(cb.equal(eq(websiteUrlPath), any(Object.class))).thenReturn(equalPredicate);
            when(cb.like(eq(websiteUrlPath), anyString())).thenReturn(likePredicate);
            when(cb.or(any(Predicate[].class))).thenReturn(orPredicate);

            attribute = OrderQueryHelper.buildHostMatchAttribute(keyword);
        }

        Predicate run() {
            return attribute.toPredicate(root, query, cb);
        }
    }
}
