package com.sca4d.iot.api.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 验证字符串 pattern 必须声明 `maximumLength`，并在构建时完成编译。 */
class ThingValueTypeTest {

    @Test
    void matchesCompiledStringPattern() {
        ThingValueType netId = ThingValueType.stringType()
                .maximumLength(4)
                .pattern("[0-9a-f]{4}")
                .build();

        assertTrue(netId.matchesPattern("12af"));
        assertFalse(netId.matchesPattern("12AF"));
    }

    @Test
    void rejectsPatternWithoutMaximumLength() {
        ThingValueType.Builder value = ThingValueType.stringType().pattern("[0-9a-f]+");

        assertThrows(IllegalArgumentException.class, value::build);
    }

    @Test
    void rejectsInvalidJavaPatternWhenDeclared() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ThingValueType.stringType().maximumLength(4).pattern("["));
    }
}
