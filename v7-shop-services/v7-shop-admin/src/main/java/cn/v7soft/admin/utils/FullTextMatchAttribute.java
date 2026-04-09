package cn.v7soft.admin.utils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import cn.v7soft.core.controller.request.attributes.QueryAttribute;

/**
 * 全文索引搜索属性，生成 MATCH(column) AGAINST('keyword' IN BOOLEAN MODE)。
 * 依赖 MySQLDialectConfig 中注册的 match_against 自定义函数。
 */
public class FullTextMatchAttribute implements QueryAttribute {

    private final String columnPath;
    private final String keyword;

    public FullTextMatchAttribute(String columnPath, String keyword) {
        this.columnPath = columnPath;
        this.keyword = keyword;
    }

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Path<String> path = resolvePath(root, columnPath);
        String booleanKeyword = toBooleanModeKeyword(keyword);
        Expression<Double> score = cb.function("match_against", Double.class, path, cb.literal(booleanKeyword));
        return cb.gt(score, 0.0);
    }

    /**
     * 将用户输入转为 BOOLEAN MODE 关键词格式。
     * 每个词前加 '+' 表示必须包含（AND 语义），与 LIKE '%x%' 的行为一致。
     */
    private static String toBooleanModeKeyword(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String[] words = trimmed.split("\\s+");
        if (words.length == 1) {
            return "+" + words[0] + "*";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append("+").append(word).append("* ");
            }
        }
        return sb.toString().trim();
    }

    private static <T> Path<String> resolvePath(Root<T> root, String name) {
        String[] parts = name.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            path = path.get(part);
        }
        @SuppressWarnings("unchecked")
        Path<String> result = (Path<String>) path;
        return result;
    }
}
