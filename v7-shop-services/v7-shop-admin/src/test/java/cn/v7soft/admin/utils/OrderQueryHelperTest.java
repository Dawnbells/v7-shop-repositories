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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderQueryHelper 单元测试。
 *
 * <p>核心验证 "按下单域名查询" 的匹配语义：
 * <ul>
 *   <li>一级域名（点数 ≤ 1，如 "varlar.com"）：websiteUrl = keyword OR websiteUrl LIKE '%.${keyword}'</li>
 *   <li>子域名（点数 ≥ 2，如 "de.varlar.com"）：仅 websiteUrl = keyword（不再做后缀 LIKE）</li>
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
    @DisplayName("buildHostMatchAttribute: 一级域名（点数=1）= equal OR LIKE '%.keyword'，匹配自身和全部子域名")
    void buildHostMatchAttributeForTopLevelDomain() {
        String keyword = "varlar.com";
        HostMatchHarness harness = new HostMatchHarness(keyword);

        harness.run();

        verify(harness.cb, times(1)).equal(harness.websiteUrlPath, keyword);
        verify(harness.cb, times(1)).like(harness.websiteUrlPath, "%." + keyword);
        verify(harness.cb, times(1)).or(any(Predicate.class), any(Predicate.class));
    }

    @Test
    @DisplayName("buildHostMatchAttribute: 子域名（点数≥2）只用 equal，不做后缀 LIKE")
    void buildHostMatchAttributeForSubdomainUsesExactEqualOnly() {
        // keyword=de.varlar.com 视为子域名，仅精确匹配自身；
        // 避免 LIKE 既会引入 m.de.varlar.com 这类更深子域名的误匹配，
        // 也保证 de.varlar.com 与 ide.varlar.com 的严格区分（这本来就不会被 LIKE 误匹配，因为 LIKE 是 "%."+keyword 形态）。
        String keyword = "de.varlar.com";
        HostMatchHarness harness = new HostMatchHarness(keyword);

        harness.run();

        verify(harness.cb, times(1)).equal(harness.websiteUrlPath, keyword);
        verify(harness.cb, never()).like(eq(harness.websiteUrlPath), anyString());
        verify(harness.cb, never()).or(any(Predicate.class), any(Predicate.class));
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
