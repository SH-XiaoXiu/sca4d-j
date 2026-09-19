package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.concurrent.LockKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class LockKeyTest {

    @Test
    @DisplayName("Builds A Lock Key From Valid Resource Name Segments")
    void buildsALockKeyFromValidResourceNameSegments() {
        LockKey key = new LockKey("platform", "profile", "42");

        assertEquals(List.of("platform", "profile", "42"), key.segments());
        assertEquals("platform:profile:42", key.value());
    }

    @Test
    @DisplayName("Rejects Ambiguous Lock Key Segments")
    void rejectsAmbiguousLockKeySegments() {
        assertThrows(IllegalArgumentException.class, LockKey::new);
        assertThrows(IllegalArgumentException.class, () -> new LockKey("platform:profile"));
        assertThrows(IllegalArgumentException.class, () -> new LockKey("platform profile"));
        assertThrows(IllegalArgumentException.class, () -> new LockKey(" platform"));
        assertThrows(IllegalArgumentException.class, () -> new LockKey("platform", ""));
    }
}
