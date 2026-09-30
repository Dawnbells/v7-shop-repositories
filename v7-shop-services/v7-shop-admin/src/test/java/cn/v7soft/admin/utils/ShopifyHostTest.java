package cn.v7soft.admin.utils;

import cn.v7soft.core.exception.BaseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ShopifyHostTest {
    @ParameterizedTest
    @ValueSource(strings = {"test-shop", " TEST-SHOP ", "test-shop.myshopify.com",
            "https://TEST-SHOP.myshopify.com", "http://test-shop.myshopify.com/", "https://test-shop.myshopify.com/admin"})
    void normalizesShopifyAddresses(String input) {
        String handle = ShopifyHost.normalize(input);
        assertEquals("test-shop", handle);
        assertEquals("test-shop.myshopify.com", URI.create("https://" + ShopifyHost.host(handle)
                + "/admin/oauth/access_token").getHost());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"audit.example?", "audit.example#", "audit.example%3f", "test_shop", "-shop", "shop-",
            "https://audit.example/", "https://test-shop.myshopify.com@audit.example/",
            "https://audit.example@test-shop.myshopify.com/", "https://test-shop.myshopify.com:443/",
            "https://test-shop.myshopify.com?x=1", "https://test-shop.myshopify.com#x",
            "shop.myshopify.com.evil.example", "a.b.myshopify.com", "test-shop\\@audit.example?"})
    void rejectsUntrustedHosts(String input) {
        assertThrows(BaseException.class, () -> ShopifyHost.normalize(input));
    }
}
