package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.name.Display;
import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.AttributeConstraint;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationAxes;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class OperationAxesTest {

    @Test
    @DisplayName("Standard Axes Declare Intent And Nature As Required And Risk As Optional")
    void standardAxesDeclareConstraints() {
        assertEquals(
                List.of(OperationAxes.INTENT, OperationAxes.NATURE, OperationAxes.RISK, OperationAxes.VISIBILITY),
                OperationAxes.standard());
        assertEquals(AttributeConstraint.OPTIONAL, OperationAxes.VISIBILITY.constraint());
        assertTrue(OperationAxes.VISIBILITY.contains(OperationAxes.Visibility.UNLISTED));
        assertEquals(AttributeConstraint.REQUIRED_EXCLUSIVE, OperationAxes.INTENT.constraint());
        assertEquals(AttributeConstraint.REQUIRED_EXCLUSIVE, OperationAxes.NATURE.constraint());
        assertEquals(AttributeConstraint.OPTIONAL, OperationAxes.RISK.constraint());
    }

    @Test
    @DisplayName("Every Standard Value Belongs To Its Own Axis")
    void everyStandardValueBelongsToItsAxis() {
        List<Attribute> intents = List.of(
                OperationAxes.Intent.READ,
                OperationAxes.Intent.ACCESS,
                OperationAxes.Intent.CREATE,
                OperationAxes.Intent.UPDATE,
                OperationAxes.Intent.DELETE,
                OperationAxes.Intent.OPERATE,
                OperationAxes.Intent.EXPORT,
                OperationAxes.Intent.IMPORT,
                OperationAxes.Intent.ASSIGN,
                OperationAxes.Intent.SEND,
                OperationAxes.Intent.RESET,
                OperationAxes.Intent.CONFIRM,
                OperationAxes.Intent.FORCE,
                OperationAxes.Intent.REVEAL);
        for (Attribute intent : intents) {
            assertTrue(OperationAxes.INTENT.contains(intent), intent.toString());
            assertFalse(OperationAxes.NATURE.contains(intent), intent.toString());
        }
        assertTrue(OperationAxes.NATURE.contains(OperationAxes.Nature.FUNCTION));
        assertTrue(OperationAxes.NATURE.contains(OperationAxes.Nature.DATA_SCOPE));
        List<Attribute> risks = List.of(
                OperationAxes.Risk.IRREVERSIBLE,
                OperationAxes.Risk.PRIVILEGE_ESCALATION,
                OperationAxes.Risk.SCOPE_WIDENING,
                OperationAxes.Risk.CREDENTIAL_DISCLOSURE,
                OperationAxes.Risk.CREDENTIAL_MANAGEMENT);
        for (Attribute risk : risks) {
            assertTrue(OperationAxes.RISK.contains(risk), risk.toString());
        }
    }

    @Test
    @DisplayName("Write Intents Are Under INTENT:WRITE And Read Or Access Are Not")
    void writeIntentsAreUnderWrite() {
        assertTrue(OperationAxes.Intent.DELETE.isUnder(OperationAxes.Intent.WRITE));
        assertTrue(OperationAxes.Intent.REVEAL.isUnder(OperationAxes.Intent.WRITE));
        assertFalse(OperationAxes.Intent.READ.isUnder(OperationAxes.Intent.WRITE));
        assertFalse(OperationAxes.Intent.ACCESS.isUnder(OperationAxes.Intent.WRITE));
        assertEquals(new Attribute("INTENT:WRITE:EXPORT"), OperationAxes.Intent.EXPORT);
        assertEquals(new Attribute("RISK:PRIVILEGE:ESCALATION"), OperationAxes.Risk.PRIVILEGE_ESCALATION);
    }

    @Test
    @DisplayName("Catalog Requires Intent And Nature On Every Leaf Under Standard Axes")
    void catalogRequiresIntentAndNature() {
        OperationModule missingNature = () -> OperationNode.root(
                TreeCode.ofSegment("ORDERS"),
                Display.of("operation.orders"),
                10,
                OperationNode.leaf(Action.READ, Display.of("operation.orders.read"), 10, OperationAxes.Intent.READ));
        OperationModule complete = () -> OperationNode.root(
                TreeCode.ofSegment("ORDERS"),
                Display.of("operation.orders"),
                10,
                OperationNode.leaf(
                        Action.READ,
                        Display.of("operation.orders.read"),
                        10,
                        OperationAxes.Intent.READ,
                        OperationAxes.Nature.FUNCTION));

        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(missingNature), OperationAxes.standard()));
        assertEquals(1, new OperationCatalog(List.of(complete), OperationAxes.standard()).leaves().size());
    }
}
