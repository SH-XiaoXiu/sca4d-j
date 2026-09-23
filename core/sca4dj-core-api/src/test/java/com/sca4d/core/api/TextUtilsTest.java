package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.utils.TextUtils;
import org.junit.jupiter.api.Test;

/** 验证普通文本的必填、可选、精确匹配和长度约束。 */
class TextUtilsTest {

    @Test
    void detectsTextWithoutNormalizingIt() {
        assertFalse(TextUtils.hasText(null));
        assertFalse(TextUtils.hasText(" \u2003 "));
        assertTrue(TextUtils.hasText(" value "));
    }

    @Test
    void normalizesRequiredAndOptionalText() {
        assertEquals("value", TextUtils.requireText(" \u2003value\u2003 ", "字段"));
        assertEquals("value", TextUtils.optionalText(" value "));
        assertNull(TextUtils.optionalText(" \u2003 "));
    }

    @Test
    void preservesExactText() {
        String value = " value ";

        assertSame(value, TextUtils.requireExactText(value, "字段"));
        assertThrows(IllegalArgumentException.class, () -> TextUtils.requireExactText(" \u2003 ", "字段"));
    }

    @Test
    void enforcesMaximumLengthAfterNormalization() {
        assertEquals("abc", TextUtils.requireText(" abc ", "字段", 3));
        assertEquals("abc", TextUtils.optionalText(" abc ", "字段", 3));
        assertThrows(IllegalArgumentException.class, () -> TextUtils.requireText("abcd", "字段", 3));
        assertThrows(IllegalArgumentException.class, () -> TextUtils.optionalText("abcd", "字段", 3));
        assertThrows(IllegalArgumentException.class, () -> TextUtils.requireText("a", "字段", 0));
    }
}
