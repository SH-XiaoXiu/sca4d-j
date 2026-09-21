package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.code.TreeCode;
import org.junit.jupiter.api.Test;

/** 验证系统层级编码的大小写、分隔符和层级约束。 */
class TreeCodeTest {

    @Test
    void normalizesCaseAndKeepsSeparator() {
        TreeCode slash = TreeCode.of("platform/users/profiles");
        TreeCode colon = TreeCode.of("Platform:Users:Profiles");

        assertEquals("PLATFORM/USERS/PROFILES", slash.value());
        assertEquals('/', slash.separator());
        assertEquals("PLATFORM:USERS:PROFILES", colon.value());
        assertEquals(':', colon.separator());
    }

    @Test
    void rejectsInvalidHierarchy() {
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PROFILE"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PLATFORM/USERS:PROFILES"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PLATFORM/USER-PROFILES"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PLATFORM//PROFILES"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PLATFORM.USERS"));
    }
}
