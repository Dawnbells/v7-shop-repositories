package cn.v7soft.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import cn.v7soft.admin.controller.req.BatchEditMerchandiseRequest.EmptyResultPolicy;

class MerchandiseFieldEditorTest {

    @Test
    void removesExactFieldFromPrefixedName() {
        MerchandiseFieldEditor.Result result = MerchandiseFieldEditor.remove(
                "PT面具=锅具套装/锦鲤洗车机/锂电洗车机/万能工具套装",
                "万能工具套装",
                "/",
                EmptyResultPolicy.SKIP);

        assertEquals(MerchandiseFieldEditor.Outcome.UPDATED, result.outcome());
        assertEquals("PT面具=锅具套装/锦鲤洗车机/锂电洗车机", result.value());
    }

    @Test
    void appendsFieldAndKeepsPrefix() {
        MerchandiseFieldEditor.Result result = MerchandiseFieldEditor.add(
                "PT面具=锅具套装/喷水机/万能工具套装", "XXXX", "/");

        assertEquals(MerchandiseFieldEditor.Outcome.UPDATED, result.outcome());
        assertEquals("PT面具=锅具套装/喷水机/万能工具套装/XXXX", result.value());
    }

    @Test
    void treatsNameWithoutEqualsAsFieldList() {
        MerchandiseFieldEditor.Result added = MerchandiseFieldEditor.add("锅具套装|喷水机", "XXXX", "|");
        MerchandiseFieldEditor.Result removed = MerchandiseFieldEditor.remove(
                added.value(), "喷水机", "|", EmptyResultPolicy.SKIP);

        assertEquals("锅具套装|喷水机|XXXX", added.value());
        assertEquals("锅具套装|XXXX", removed.value());
    }

    @Test
    void doesNotDeleteSubstringAndSkipsDuplicateAdd() {
        MerchandiseFieldEditor.Result removeResult = MerchandiseFieldEditor.remove(
                "PT=锂电洗车机/洗车机", "锂电", "/", EmptyResultPolicy.SKIP);
        MerchandiseFieldEditor.Result addResult = MerchandiseFieldEditor.add(
                "PT=锂电洗车机/洗车机", "洗车机", "/");

        assertEquals(MerchandiseFieldEditor.Outcome.NOT_FOUND, removeResult.outcome());
        assertEquals(MerchandiseFieldEditor.Outcome.ALREADY_EXISTS, addResult.outcome());
    }

    @Test
    void removesAllDuplicateOccurrences() {
        MerchandiseFieldEditor.Result result = MerchandiseFieldEditor.remove(
                "PT=A/B/A/C", "A", "/", EmptyResultPolicy.SKIP);

        assertEquals("PT=B/C", result.value());
    }

    @Test
    void supportsBothEmptyResultPolicies() {
        MerchandiseFieldEditor.Result skipped = MerchandiseFieldEditor.remove(
                "PT=A", "A", "/", EmptyResultPolicy.SKIP);
        MerchandiseFieldEditor.Result prefixedEmpty = MerchandiseFieldEditor.remove(
                "PT=A", "A", "/", EmptyResultPolicy.KEEP_EMPTY);
        MerchandiseFieldEditor.Result plainEmpty = MerchandiseFieldEditor.remove(
                "A", "A", "/", EmptyResultPolicy.KEEP_EMPTY);

        assertEquals(MerchandiseFieldEditor.Outcome.EMPTY_SKIPPED, skipped.outcome());
        assertEquals("PT=A", skipped.value());
        assertEquals("PT=", prefixedEmpty.value());
        assertTrue(prefixedEmpty.emptied());
        assertEquals("", plainEmpty.value());
        assertTrue(plainEmpty.emptied());
    }

    @Test
    void appendsDirectlyAfterExistingTrailingDelimiter() {
        MerchandiseFieldEditor.Result result = MerchandiseFieldEditor.add("PT=A/", "B", "/");

        assertEquals("PT=A/B", result.value());
        assertFalse(result.emptied());
    }
}
