package cn.v7soft.admin.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import cn.v7soft.admin.controller.req.BatchEditMerchandiseRequest.EmptyResultPolicy;

final class MerchandiseFieldEditor {

    enum Outcome {
        UPDATED,
        ALREADY_EXISTS,
        NOT_FOUND,
        EMPTY_SKIPPED
    }

    record Result(String value, Outcome outcome, boolean emptied) {
    }

    private MerchandiseFieldEditor() {
    }

    static Result add(String merchandise, String field, String delimiter) {
        NameParts parts = splitName(merchandise);
        List<String> fields = splitFields(parts.payload(), delimiter);
        if (fields.stream().anyMatch(field::equals)) {
            return new Result(merchandise, Outcome.ALREADY_EXISTS, false);
        }

        String value;
        if (parts.payload().isEmpty()) {
            value = parts.prefix() + field;
        } else if (parts.payload().endsWith(delimiter)) {
            value = parts.original() + field;
        } else {
            value = parts.original() + delimiter + field;
        }
        return new Result(value, Outcome.UPDATED, false);
    }

    static Result remove(String merchandise, String field, String delimiter, EmptyResultPolicy emptyResultPolicy) {
        NameParts parts = splitName(merchandise);
        List<String> fields = splitFields(parts.payload(), delimiter);
        List<String> remaining = new ArrayList<>(fields.size());
        boolean found = false;
        for (String existingField : fields) {
            if (field.equals(existingField)) {
                found = true;
            } else {
                remaining.add(existingField);
            }
        }
        if (!found) {
            return new Result(merchandise, Outcome.NOT_FOUND, false);
        }

        boolean hasNonEmptyField = remaining.stream().anyMatch(value -> !value.isEmpty());
        if (!hasNonEmptyField) {
            if (emptyResultPolicy == EmptyResultPolicy.SKIP) {
                return new Result(merchandise, Outcome.EMPTY_SKIPPED, false);
            }
            return new Result(parts.prefix(), Outcome.UPDATED, true);
        }

        return new Result(parts.prefix() + String.join(delimiter, remaining), Outcome.UPDATED, false);
    }

    static Result overwrite(String merchandise, String field) {
        NameParts parts = splitName(merchandise);
        String value = parts.prefix() + field;
        if (value.equals(parts.original())) {
            return new Result(parts.original(), Outcome.ALREADY_EXISTS, false);
        }
        return new Result(value, Outcome.UPDATED, false);
    }

    static boolean matchesOriginal(String merchandise, String originalMerchandise, String delimiter) {
        NameParts parts = splitName(merchandise);
        if (parts.prefix().isEmpty()) {
            return originalMerchandise.equals(parts.original());
        }
        return splitFields(parts.payload(), delimiter).stream().anyMatch(originalMerchandise::equals);
    }

    private static NameParts splitName(String merchandise) {
        String original = merchandise == null ? "" : merchandise;
        int prefixEnd = original.indexOf('=');
        if (prefixEnd < 0) {
            return new NameParts(original, "", original);
        }
        return new NameParts(original, original.substring(0, prefixEnd + 1), original.substring(prefixEnd + 1));
    }

    private static List<String> splitFields(String payload, String delimiter) {
        return Arrays.asList(payload.split(Pattern.quote(delimiter), -1));
    }

    private record NameParts(String original, String prefix, String payload) {
    }
}
