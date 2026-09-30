package cn.v7soft.admin.utils;

import cn.v7soft.core.enums.ClientResponseEnum;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Keeps both saved handles and outbound credential requests on Shopify hosts. */
public final class ShopifyHost {
    private static final String SUFFIX = ".myshopify.com";
    private static final Pattern HANDLE = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private ShopifyHost() {
    }

    public static String normalize(String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("https://") || value.startsWith("http://")) {
            URI uri;
            try {
                uri = URI.create(value);
            } catch (IllegalArgumentException e) {
                throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("Shopify店铺地址格式不正确");
            }
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(uri.getHost() != null
                            && uri.getHost().endsWith(SUFFIX) && uri.getRawUserInfo() == null
                            && uri.getPort() == -1 && uri.getRawQuery() == null && uri.getRawFragment() == null,
                    "请输入有效的Shopify店铺地址");
            value = uri.getHost();
        }
        if (value.endsWith(SUFFIX)) {
            value = value.substring(0, value.length() - SUFFIX.length());
        }
        validate(value);
        return value;
    }

    public static String host(String handle) {
        // Revalidate at the HTTP boundary, including handles already stored in the database.
        validate(handle);
        return handle + SUFFIX;
    }

    private static void validate(String handle) {
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(handle != null && HANDLE.matcher(handle).matches(),
                "Shopify HANDLE只能包含字母、数字和中划线，长度为1至63，且首尾不能为中划线");
    }
}
