package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributeTest {

    @Test
    @DisplayName("Parses Text And Code Into Attribute")
    void parsesTextAndCode() {
        Attribute fromText = new Attribute("intent:read");
        Attribute fromCode = new Attribute(TreeCode.ofColon("INTENT:READ"));

        assertEquals(fromText, fromCode);
        assertEquals("INTENT:READ", fromText.format(NameFormat.COLON));
    }

    @Test
    @DisplayName("Rejects Wildcard And Over Long Attribute")
    void rejectsWildcardAndOverLongAttribute() {
        assertThrows(IllegalArgumentException.class, () -> new Attribute("INTENT:*"));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("A".repeat(129)));
        assertEquals(128, new Attribute("A".repeat(128)).format(NameFormat.COLON).length());
    }

    @Test
    @DisplayName("Keeps Attribute Type When Building Children")
    void keepsAttributeTypeWhenBuildingChildren() {
        Attribute root = new Attribute("RISK");

        Attribute child = root.child("IRREVERSIBLE");
        Attribute nested = root.child(TreeCode.ofSegment("PRIVILEGE")).child("ESCALATION");
        Attribute appended = root.append(TreeCode.ofColon("PRIVILEGE:ESCALATION"));

        assertEquals(new Attribute("RISK:IRREVERSIBLE"), child);
        assertEquals(new Attribute("RISK:PRIVILEGE:ESCALATION"), nested);
        assertEquals(nested, appended);
        assertThrows(IllegalArgumentException.class, () -> root.child("*"));
    }

    @Test
    @DisplayName("Attribute Is Not Equal To TreeCode With Same Nodes")
    void attributeIsNotEqualToTreeCodeWithSameNodes() {
        Attribute attribute = new Attribute("INTENT:READ");
        TreeCode code = TreeCode.ofColon("INTENT:READ");

        assertNotEquals(attribute, code);
        assertNotEquals(code, attribute);
        assertTrue(attribute.sameCode(code));
        assertTrue(attribute.isUnder(new Attribute("INTENT")));
        assertFalse(new Attribute("INTENT").isUnder(attribute));
    }
}
