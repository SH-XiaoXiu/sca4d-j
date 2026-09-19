package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuntimeNamespaceTest {

    @Test
    @DisplayName("Builds Sca4d-J Resource Names Under The Application ID")
    void buildsSca4djResourceNamesUnderTheApplicationId() {
        RuntimeNamespace namespace = new RuntimeNamespace("example-app");

        assertEquals("example-app", namespace.applicationId());
        assertEquals(
                "example-app:sca4dj:auth:session-generation:42",
                namespace.sca4dj("auth", "session-generation", "42"));
    }

    @Test
    @DisplayName("Rejects Ambiguous Resource Name Segments")
    void rejectsAmbiguousResourceNameSegments() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeNamespace("example:app"));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeNamespace("example app"));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeNamespace(" example-app"));
        RuntimeNamespace namespace = new RuntimeNamespace("example-app");
        assertThrows(IllegalArgumentException.class, () -> namespace.sca4dj("auth:session"));
        assertThrows(IllegalArgumentException.class, namespace::sca4dj);
    }
}
