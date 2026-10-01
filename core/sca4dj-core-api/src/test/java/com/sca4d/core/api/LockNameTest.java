package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证锁键复用 Key 的结构并保留独立领域身份。 */
class LockNameTest {

    @Test
    void sharesKeyEncodingAndPreservesTheLockType() {
        LockKey key = new LockKey(TreeCode.of("PLATFORM.PROFILE"), 42L);
        assertEquals(List.of("LOCK", "PLATFORM", "PROFILE", "42"), key.segments());
        LockKey child = key.part("a:b");
        assertEquals("V_613A62", child.segments().get(4));
        assertEquals(4, key.segments().size());
    }

    @Test
    void rejectsWildcardLockResourcesAndPreservesDynamicCase() {
        assertThrows(IllegalArgumentException.class, () -> new LockKey(TreeCode.of("PLATFORM:*")));
        assertNotEquals(new LockKey(TreeCode.of("PLATFORM"), "a"), new LockKey(TreeCode.of("PLATFORM"), "A"));
    }
}
