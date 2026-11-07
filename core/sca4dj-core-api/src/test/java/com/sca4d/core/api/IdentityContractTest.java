package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityContractTest {

    @Test
    @DisplayName("Requires Positive Identity IDs And Compares By Value")
    void requiresPositiveIdentityIdsAndComparesByValue() {
        assertThrows(IllegalArgumentException.class, () -> new Subject(0, SubjectType.HUMAN));
        assertThrows(IllegalArgumentException.class, () -> new Account(0, 1, new RealmKey("TEST")));
        assertThrows(IllegalArgumentException.class, () -> new Account(1, 0, new RealmKey("TEST")));

        long first = 42L;
        long second = 42L;
        assertEquals(first, second);
    }

    @Test
    @DisplayName("Accepts Only Uppercase Realm Keys")
    void acceptsOnlyUppercaseRealmKeys() {
        assertEquals("PLATFORM", new RealmKey("PLATFORM").value());
        assertEquals("WORKSPACE_2", new RealmKey("WORKSPACE_2").value());
        assertEquals(128, new RealmKey("A".repeat(128)).value().length());
        for (String invalid : new String[] {
                null, "", " ", "platform", "Platform", "PLATFORM ", "2FA", "_A", "WORK-SPACE", "A".repeat(129)
        }) {
            assertThrows(IllegalArgumentException.class, () -> new RealmKey(invalid), String.valueOf(invalid));
        }
    }

    @Test
    @DisplayName("Uses Singleton System Identity With Fixed IDs")
    void usesSingletonSystemIdentityWithFixedIds() {
        assertEquals(1, Subject.SYSTEM.subjectId());
        assertEquals(1, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")).principalId());
    }

}
