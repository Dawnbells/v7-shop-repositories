package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 Admin GraphQL 订单节点映射成 REST 形状的 JSON（与 webhook 订单 payload 同形），
 * 让订单转换器只认一种形状。映射表见 docs/features/shopify-public-app/ARCH.md §6.1。
 * <p>
 * GraphQL 不提供浏览器 UA（REST 的 client_details.user_agent），转换后 UA 为空。
 */
public final class ShopifyGraphqlOrderAdapter {
    public static final String METAFIELD_NAMESPACE = "xyz";
    public static final String METAFIELD_KEY_CN_PRODUCT_NAME = "cn_product_name";
    public static final String METAFIELD_KEY_WAYBILL_PRODUCT_NAME = "waybill_product_name";
    public static final String METAFIELD_KEY_OWNER_NAME = "owner_name";
    public static final String METAFIELD_KEY_OWNER_TELEPHONE = "owner_telephone";
    public static final String METAFIELD_KEY_SKU_CODE = "sku_code";
    public static final String METAFIELD_KEY_SKU_CODE_HIGH = "sku_code_high";
    public static final List<String> METAFIELD_KEYS = List.of(METAFIELD_KEY_CN_PRODUCT_NAME, METAFIELD_KEY_WAYBILL_PRODUCT_NAME,
            METAFIELD_KEY_OWNER_NAME, METAFIELD_KEY_OWNER_TELEPHONE, METAFIELD_KEY_SKU_CODE, METAFIELD_KEY_SKU_CODE_HIGH);
    public static final String PRODUCT_GID_PREFIX = "gid://shopify/Product/";

    private static final String[][] MONEY_SET_FIELDS = {
            {"totalPriceSet", "total_price_set", "total_price"},
            {"subtotalPriceSet", "subtotal_price_set", "subtotal_price"},
            {"totalShippingPriceSet", "total_shipping_price_set", null},
            {"totalTaxSet", "total_tax_set", "total_tax"},
            {"totalDiscountsSet", "total_discounts_set", "total_discounts"},
    };

    private ShopifyGraphqlOrderAdapter() {
    }

    /**
     * 把 GraphQL 订单节点转成 REST 形状。行项目商品内联返回的元字段写入 metafieldsCache（key = 商品 legacy id）
     * @param metafieldsCache 可为 null（不收集元字段）
     */
    public static JSONObject toRestOrder(JSONObject node, Map<String, Map<String, String>> metafieldsCache) {
        JSONObject order = new JSONObject();
        String gid = str(node, "id");
        order.set("id", StrUtil.blankToDefault(str(node, "legacyResourceId"), legacyId(gid)));
        order.set("admin_graphql_api_id", gid);
        order.set("name", str(node, "name"));
        order.set("created_at", str(node, "createdAt"));
        order.set("updated_at", str(node, "updatedAt"));
        order.set("cancelled_at", str(node, "cancelledAt"));
        order.set("cancel_reason", lower(str(node, "cancelReason")));
        order.set("closed_at", str(node, "closedAt"));
        order.set("financial_status", lower(str(node, "displayFinancialStatus")));
        order.set("fulfillment_status", fulfillmentStatus(str(node, "displayFulfillmentStatus")));
        order.set("currency", str(node, "currencyCode"));
        order.set("presentment_currency", str(node, "presentmentCurrencyCode"));

        for (String[] field : MONEY_SET_FIELDS) {
            JSONObject moneySet = moneySet(obj(node, field[0]));
            if (moneySet != null) {
                order.set(field[1], moneySet);
                if (field[2] != null) {
                    order.set(field[2], shopAmount(moneySet));
                }
            }
        }

        String email = str(node, "email");
        order.set("email", email);
        order.set("contact_email", email);
        order.set("phone", str(node, "phone"));
        order.set("customer_locale", str(node, "customerLocale"));
        order.set("note", str(node, "note"));
        order.set("tags", tags(node.get("tags")));
        order.set("source_name", str(node, "sourceName"));
        order.set("browser_ip", str(node, "clientIp"));

        JSONObject journey = obj(node, "customerJourneySummary");
        if (journey != null) {
            JSONObject visit = obj(journey, "lastVisit");
            if (visit == null) {
                visit = obj(journey, "firstVisit");
            }
            if (visit != null) {
                order.set("landing_site", str(visit, "landingPage"));
                order.set("referring_site", str(visit, "referrerUrl"));
            }
        }

        JSONArray customAttributes = arr(node, "customAttributes");
        if (customAttributes != null) {
            JSONArray noteAttributes = new JSONArray();
            for (int i = 0; i < customAttributes.size(); i++) {
                JSONObject attribute = customAttributes.getJSONObject(i);
                if (attribute != null) {
                    noteAttributes.add(new JSONObject().set("name", str(attribute, "key")).set("value", str(attribute, "value")));
                }
            }
            order.set("note_attributes", noteAttributes);
        }

        JSONObject customer = obj(node, "customer");
        if (customer != null) {
            JSONObject restCustomer = new JSONObject();
            restCustomer.set("id", StrUtil.blankToDefault(str(customer, "legacyResourceId"), legacyId(str(customer, "id"))));
            restCustomer.set("email", StrUtil.blankToDefault(nested(customer, "defaultEmailAddress", "emailAddress"), str(customer, "email")));
            restCustomer.set("first_name", str(customer, "firstName"));
            restCustomer.set("last_name", str(customer, "lastName"));
            restCustomer.set("phone", StrUtil.blankToDefault(nested(customer, "defaultPhoneNumber", "phoneNumber"), str(customer, "phone")));
            order.set("customer", restCustomer);
        }

        JSONObject shippingAddress = address(obj(node, "shippingAddress"));
        if (shippingAddress != null) {
            order.set("shipping_address", shippingAddress);
        }
        JSONObject billingAddress = address(obj(node, "billingAddress"));
        if (billingAddress != null) {
            order.set("billing_address", billingAddress);
        }

        JSONArray gateways = arr(node, "paymentGatewayNames");
        if (gateways != null) {
            order.set("payment_gateway_names", gateways);
            if (!gateways.isEmpty()) {
                order.set("gateway", gateways.getStr(0));
            }
        }

        JSONArray shippingLineNodes = connectionNodes(node, "shippingLines");
        if (shippingLineNodes != null) {
            JSONArray shippingLines = new JSONArray();
            for (int i = 0; i < shippingLineNodes.size(); i++) {
                JSONObject line = shippingLineNodes.getJSONObject(i);
                if (line == null) {
                    continue;
                }
                JSONObject restLine = new JSONObject();
                restLine.set("title", str(line, "title"));
                restLine.set("code", str(line, "code"));
                JSONObject priceSet = moneySet(obj(line, "originalPriceSet"));
                if (priceSet != null) {
                    restLine.set("price_set", priceSet);
                    restLine.set("price", shopAmount(priceSet));
                }
                shippingLines.add(restLine);
            }
            order.set("shipping_lines", shippingLines);
        }

        JSONArray lineItemNodes = connectionNodes(node, "lineItems");
        if (lineItemNodes != null) {
            JSONArray lineItems = new JSONArray();
            for (int i = 0; i < lineItemNodes.size(); i++) {
                JSONObject lineItem = lineItemNodes.getJSONObject(i);
                if (lineItem != null) {
                    lineItems.add(lineItem(lineItem, metafieldsCache));
                }
            }
            order.set("line_items", lineItems);
        }
        return order;
    }

    private static JSONObject lineItem(JSONObject node, Map<String, Map<String, String>> metafieldsCache) {
        JSONObject item = new JSONObject();
        item.set("id", legacyId(str(node, "id")));
        item.set("admin_graphql_api_id", str(node, "id"));
        item.set("name", str(node, "name"));
        item.set("title", str(node, "title"));
        item.set("variant_title", str(node, "variantTitle"));
        JSONObject variant = obj(node, "variant");
        String sku = str(node, "sku");
        if (sku == null && variant != null) {
            sku = str(variant, "sku");
        }
        item.set("sku", sku);
        item.set("quantity", node.getInt("quantity"));
        item.set("current_quantity", node.getInt("currentQuantity"));
        JSONObject priceSet = moneySet(obj(node, "originalUnitPriceSet"));
        if (priceSet != null) {
            item.set("price_set", priceSet);
            item.set("price", shopAmount(priceSet));
        }
        JSONObject totalDiscountSet = moneySet(obj(node, "totalDiscountSet"));
        if (totalDiscountSet != null) {
            item.set("total_discount_set", totalDiscountSet);
            item.set("total_discount", shopAmount(totalDiscountSet));
        }
        if (variant != null) {
            item.set("variant_id", StrUtil.blankToDefault(str(variant, "legacyResourceId"), legacyId(str(variant, "id"))));
        }
        JSONObject product = obj(node, "product");
        if (product != null) {
            String productId = StrUtil.blankToDefault(str(product, "legacyResourceId"), legacyId(str(product, "id")));
            item.set("product_id", productId);
            if (metafieldsCache != null && StrUtil.isNotBlank(productId) && product.containsKey("metafields")) {
                metafieldsCache.put(productId, parseMetafields(obj(product, "metafields")));
            }
        }
        return item;
    }

    /**
     * 解析 metafields 连接（nodes 或 edges.node），只保留 xyz 命名空间的 key → value；
     * 按 keys 参数查询时返回的 key 可能带「namespace.」前缀，统一去掉
     */
    public static Map<String, String> parseMetafields(JSONObject connection) {
        JSONArray nodes = connectionNodes(connection);
        if (nodes == null || nodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject metafield = nodes.getJSONObject(i);
            if (metafield == null) {
                continue;
            }
            String key = str(metafield, "key");
            String value = str(metafield, "value");
            if (StrUtil.isBlank(key) || value == null) {
                continue;
            }
            String namespace = str(metafield, "namespace");
            if (namespace != null && !METAFIELD_NAMESPACE.equals(namespace)) {
                continue;
            }
            result.put(StrUtil.removePrefix(key, METAFIELD_NAMESPACE + "."), value);
        }
        return result;
    }

    /**
     * gid://shopify/Order/123 → 123；不是 gid 时原样返回
     */
    public static String legacyId(String gid) {
        if (StrUtil.isBlank(gid)) {
            return null;
        }
        int slash = gid.lastIndexOf('/');
        String tail = slash >= 0 ? gid.substring(slash + 1) : gid;
        int query = tail.indexOf('?');
        return query >= 0 ? tail.substring(0, query) : tail;
    }

    public static String productGid(String productId) {
        return PRODUCT_GID_PREFIX + productId;
    }

    /**
     * 连接字段的节点数组，兼容 nodes 与 edges[].node 两种写法
     */
    public static JSONArray connectionNodes(JSONObject parent, String field) {
        return parent == null ? null : connectionNodes(obj(parent, field));
    }

    public static JSONArray connectionNodes(JSONObject connection) {
        if (connection == null) {
            return null;
        }
        JSONArray nodes = arr(connection, "nodes");
        if (nodes != null) {
            return nodes;
        }
        JSONArray edges = arr(connection, "edges");
        if (edges == null) {
            return null;
        }
        JSONArray result = new JSONArray();
        for (int i = 0; i < edges.size(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            JSONObject edgeNode = edge == null ? null : obj(edge, "node");
            if (edgeNode != null) {
                result.add(edgeNode);
            }
        }
        return result;
    }

    // ==================== 字段映射 ====================

    /**
     * displayFulfillmentStatus → REST fulfillment_status：FULFILLED→fulfilled、PARTIALLY_FULFILLED→partial、UNFULFILLED→null，其余小写
     */
    static String fulfillmentStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        return switch (status) {
            case "FULFILLED" -> "fulfilled";
            case "PARTIALLY_FULFILLED" -> "partial";
            case "UNFULFILLED" -> null;
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    private static JSONObject address(JSONObject node) {
        if (node == null) {
            return null;
        }
        JSONObject address = new JSONObject();
        address.set("first_name", str(node, "firstName"));
        address.set("last_name", str(node, "lastName"));
        address.set("name", str(node, "name"));
        address.set("company", str(node, "company"));
        address.set("phone", str(node, "phone"));
        address.set("address1", str(node, "address1"));
        address.set("address2", str(node, "address2"));
        address.set("city", str(node, "city"));
        address.set("province", str(node, "province"));
        address.set("province_code", str(node, "provinceCode"));
        address.set("zip", str(node, "zip"));
        address.set("country", str(node, "country"));
        address.set("country_code", StrUtil.blankToDefault(str(node, "countryCodeV2"), str(node, "countryCode")));
        return address;
    }

    private static JSONObject moneySet(JSONObject node) {
        if (node == null) {
            return null;
        }
        JSONObject set = new JSONObject();
        JSONObject shopMoney = money(obj(node, "shopMoney"));
        if (shopMoney != null) {
            set.set("shop_money", shopMoney);
        }
        JSONObject presentmentMoney = money(obj(node, "presentmentMoney"));
        if (presentmentMoney != null) {
            set.set("presentment_money", presentmentMoney);
        }
        return set;
    }

    private static JSONObject money(JSONObject node) {
        if (node == null) {
            return null;
        }
        return new JSONObject().set("amount", str(node, "amount")).set("currency_code", str(node, "currencyCode"));
    }

    private static String shopAmount(JSONObject moneySet) {
        JSONObject shopMoney = moneySet.getJSONObject("shop_money");
        return shopMoney == null ? null : shopMoney.getStr("amount");
    }

    private static String tags(Object raw) {
        if (raw == null || JSONUtil.isNull(raw)) {
            return null;
        }
        if (raw instanceof JSONArray array) {
            return String.join(", ", array.toList(String.class));
        }
        return String.valueOf(raw);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String nested(JSONObject parent, String field, String child) {
        JSONObject value = obj(parent, field);
        return value == null ? null : str(value, child);
    }

    private static String str(JSONObject parent, String key) {
        Object value = parent.get(key);
        if (value == null || JSONUtil.isNull(value)) {
            return null;
        }
        return value.toString();
    }

    private static JSONObject obj(JSONObject parent, String key) {
        Object value = parent.get(key);
        return value instanceof JSONObject json ? json : null;
    }

    private static JSONArray arr(JSONObject parent, String key) {
        Object value = parent.get(key);
        return value instanceof JSONArray json ? json : null;
    }
}
