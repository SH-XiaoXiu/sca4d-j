package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.OperationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationCodeTest {

    @Test
    @DisplayName("Parses Text And Code Into OperationCode")
    void parsesTextAndCode() {
        OperationCode fromText = new OperationCode("orders:invoices:read");
        OperationCode fromCode = new OperationCode(TreeCode.ofColon("ORDERS:INVOICES:READ"));

        assertEquals(fromText, fromCode);
        assertEquals("ORDERS:INVOICES:READ", fromText.format(NameFormat.COLON));
    }

    @Test
    @DisplayName("Allows Wildcard Only As Last Node")
    void allowsWildcardOnlyAsLastNode() {
        assertEquals("CATALOG:ACCESS:*", new OperationCode("CATALOG:ACCESS:*").format(NameFormat.COLON));
        assertThrows(IllegalArgumentException.class, () -> new OperationCode("ORDERS:*:READ"));
        assertThrows(IllegalArgumentException.class, () -> new OperationCode("*:ACCESS:READ"));
    }

    @Test
    @DisplayName("Reads Last Node As Verb And Detects Wildcard")
    void readsLastNodeAsVerb() {
        OperationCode read = new OperationCode("ORDERS:INVOICES:ITEMS:READ");
        OperationCode access = new OperationCode("CATALOG:ACCESS:*");

        assertEquals(TreeCode.ofSegment("READ"), read.verb());
        assertFalse(read.isWildcard());
        assertEquals(TreeCode.ofSegment("*"), access.verb());
        assertTrue(access.isWildcard());
        assertEquals(TreeCode.ofSegment("EXPORT"), new OperationCode("ORDERS:EXPORT").verb());
    }

    @Test
    @DisplayName("Rejects Single Node And Over Long Code")
    void rejectsSingleNodeAndOverLongCode() {
        assertThrows(IllegalArgumentException.class, () -> new OperationCode("ORDERS"));
        assertThrows(IllegalArgumentException.class, () -> new OperationCode("A:" + "B".repeat(127)));
        assertEquals(128, new OperationCode("A:" + "B".repeat(126)).format(NameFormat.COLON).length());
    }

    @Test
    @DisplayName("Keeps OperationCode Type When Building Children")
    void keepsOperationCodeTypeWhenBuildingChildren() {
        OperationCode parent = new OperationCode("ORDERS:INVOICES");

        OperationCode child = parent.child("READ");
        OperationCode nested = parent.child(TreeCode.ofSegment("ITEMS")).child("READ");
        OperationCode appended = parent.append(TreeCode.ofColon("ITEMS:READ"));

        assertEquals(new OperationCode("ORDERS:INVOICES:READ"), child);
        assertEquals(new OperationCode("ORDERS:INVOICES:ITEMS:READ"), nested);
        assertEquals(nested, appended);
        assertThrows(IllegalArgumentException.class, () -> parent.child("*").child("READ"));
    }

    @Test
    @DisplayName("OperationCode Is Not Equal To TreeCode With Same Nodes")
    void operationCodeIsNotEqualToTreeCodeWithSameNodes() {
        OperationCode operation = new OperationCode("ORDERS:INVOICES:READ");
        TreeCode code = TreeCode.ofColon("ORDERS:INVOICES:READ");

        assertNotEquals(operation, code);
        assertNotEquals(code, operation);
        assertTrue(operation.sameCode(code));
    }
}
