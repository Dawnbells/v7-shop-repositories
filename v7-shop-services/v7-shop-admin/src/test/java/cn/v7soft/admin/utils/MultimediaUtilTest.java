package cn.v7soft.admin.utils;

import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.dao.entities.primary.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MultimediaUtilTest {

    @Test
    void replacementIntroductionsMultimediaKeepsNullIntroduction() {
        assertNull(MultimediaUtil.replacementIntroductionsMultimedia(null, null));
    }

    @Test
    void replacementIntroductionsMultimediaKeepsBlankIntroduction() {
        assertEquals("", MultimediaUtil.replacementIntroductionsMultimedia(null, ""));
        assertEquals("   ", MultimediaUtil.replacementIntroductionsMultimedia(null, "   "));
    }

    @Test
    void productResponseAllowsNullIntroduction() {
        Product product = Product.builder()
                .id(1L)
                .title("title")
                .merchandise("merchandise")
                .build();

        ProductResponse response = assertDoesNotThrow(() -> ProductResponse.convertEntity(null, product));

        assertNull(response.getIntroduction());
    }
}
