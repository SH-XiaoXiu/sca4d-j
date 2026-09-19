package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class OperationCatalogTest {

    @Test
    @DisplayName("Expands Module Anchors And Builds Sorted Action Index")
    void expandsModuleAnchorsAndBuildsSortedActionIndex() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        module("platform", 20),
                        module("account", 10)));

        assertEquals(
                List.of("account", "platform"),
                catalog.tree().stream().map(OperationNode::code).toList());
        assertEquals(
                List.of("account:items:read", "account:items:manage", "platform:items:read", "platform:items:manage"),
                catalog.actions().stream().map(OperationNode::code).toList());
        assertEquals("platform:items", catalog.actionByCode("platform:items:read").parentCode());
    }

    @Test
    @DisplayName("Projects Tree To Selected Operations")
    void projectsTreeToSelectedOperations() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        module("platform", 20),
                        module("account", 10)));

        List<OperationNode.Module> projected = catalog.tree(
                List.of(
                        new Operation("account:items:manage"),
                        new Operation("platform:items:read")));

        assertEquals(List.of("account", "platform"), projected.stream().map(OperationNode::code).toList());
        assertEquals(
                List.of("account:items:manage"),
                projected.get(0).children().getFirst().children().stream().map(OperationNode::code).toList());
        assertEquals(
                List.of("platform:items:read"),
                projected.get(1).children().getFirst().children().stream().map(OperationNode::code).toList());
    }

    @Test
    @DisplayName("Allows Same Local Codes In Different Modules")
    void allowsSameLocalCodesInDifferentModules() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        module("project", 10),
                        module("inventory", 20)));

        assertEquals(
                List.of(
                        "project:items:read",
                        "project:items:manage",
                        "inventory:items:read",
                        "inventory:items:manage"),
                catalog.actions().stream().map(OperationNode::code).toList());
    }

    @Test
    @DisplayName("Rejects Invalid Or Duplicate Module Declarations")
    void rejectsInvalidOrDuplicateModuleDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module("platform", 10), module("platform", 20))));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.module(" ", "operation.invalid", 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.module(
                        "platform",
                        "operation.platform",
                        10,
                        OperationNode.resource("roles", "roles", 10),
                        OperationNode.resource("roles", "duplicate", 20)));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.module(
                        "platform",
                        "operation.platform",
                        10,
                        OperationNode.resource(
                                "roles",
                                "roles",
                                10,
                                OperationNode.action(Action.READ_VALUE, "read", 10),
                                OperationNode.action(Action.READ_VALUE, "duplicate", 20))));
    }

    @Test
    @DisplayName("Rejects Unknown Operations In Lookup And Projection")
    void rejectsUnknownOperationsInLookupAndProjection() {
        OperationCatalog catalog = new OperationCatalog(List.of(module("platform", 10)));

        assertThrows(IllegalArgumentException.class, () -> catalog.actionByCode("platform:unknown:read"));
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.tree(List.of(new Operation("platform:unknown:read"))));
    }

    @Test
    @DisplayName("Fingerprint Changes Only With Concrete Operation Codes")
    void fingerprintsConcreteOperationCodes() {
        OperationCatalog first = new OperationCatalog(
                List.of(
                        module("platform", 20),
                        module("account", 10)));
        OperationCatalog presentationChanged = new OperationCatalog(
                List.of(
                        module("account", 200),
                        module("platform", 100)));
        OperationCatalog operationsChanged = new OperationCatalog(
                List.of(module("platform", 20)));

        assertEquals(first.fingerprint(), presentationChanged.fingerprint());
        assertNotEquals(first.fingerprint(), operationsChanged.fingerprint());
    }

    /** @return 声明具有相同局部资源动作的测试 Operation 模块 */
    private static OperationModule module(String anchor, int sortOrder) {
        return () -> OperationNode.module(
                anchor,
                "operation." + anchor,
                sortOrder,
                OperationNode.resource(
                        "items",
                        "operation." + anchor + ".items",
                        10,
                        OperationNode.action(Action.READ_VALUE, "operation." + anchor + ".items.read", 10),
                        OperationNode.action(Action.MANAGE_VALUE, "operation." + anchor + ".items.manage", 20)));
    }
}
