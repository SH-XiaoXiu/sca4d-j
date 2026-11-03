package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.AttributeAxis;
import com.sca4d.core.api.name.code.AttributeConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributeAxisTest {

    @Test
    @DisplayName("Exposes Root And Constraint")
    void exposesRootAndConstraint() {
        Attribute root = new Attribute("INTENT");
        AttributeAxis axis = new AttributeAxis(root, AttributeConstraint.REQUIRED_EXCLUSIVE);

        assertSame(root, axis.root());
        assertEquals(AttributeConstraint.REQUIRED_EXCLUSIVE, axis.constraint());
    }

    @Test
    @DisplayName("Contains Only Strict Descendants Of Root")
    void containsOnlyStrictDescendants() {
        AttributeAxis axis = new AttributeAxis(new Attribute("RISK"), AttributeConstraint.OPTIONAL);

        assertTrue(axis.contains(new Attribute("RISK:IRREVERSIBLE")));
        assertTrue(axis.contains(new Attribute("RISK:PRIVILEGE:ESCALATION")));
        assertFalse(axis.contains(new Attribute("RISK")));
        assertFalse(axis.contains(new Attribute("INTENT:READ")));
        assertFalse(axis.contains(new Attribute("RISKY:READ")));
    }

    @Test
    @DisplayName("Rejects Null Arguments")
    void rejectsNullArguments() {
        Attribute root = new Attribute("RISK");

        assertThrows(NullPointerException.class, () -> new AttributeAxis(null, AttributeConstraint.OPTIONAL));
        assertThrows(NullPointerException.class, () -> new AttributeAxis(root, null));
        assertThrows(
                NullPointerException.class,
                () -> new AttributeAxis(root, AttributeConstraint.OPTIONAL).contains(null));
    }
}
