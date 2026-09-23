package com.sca4d.core.api.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 验证公共 Marker 不能被调用方改写为引用其他 Marker。 */
class LogMarkTest {

    @Test
    void keepsAfterCommitMarkerImmutable() {
        assertEquals("SCA4DJ_AFTER_COMMIT", LogMark.AFTER_COMMIT.getName());
        assertFalse(LogMark.AFTER_COMMIT.hasReferences());
        assertThrows(
                UnsupportedOperationException.class,
                () -> LogMark.AFTER_COMMIT.add(LogMark.AFTER_COMMIT));
        assertThrows(
                UnsupportedOperationException.class,
                () -> LogMark.AFTER_COMMIT.remove(LogMark.AFTER_COMMIT));
    }
}
