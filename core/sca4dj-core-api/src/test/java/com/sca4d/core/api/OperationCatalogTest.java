package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.name.Display;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.AttributeAxis;
import com.sca4d.core.api.name.code.AttributeConstraint;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationCode;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.core.api.permission.OperationNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class OperationCatalogTest {

    private static final Attribute INTENT = new Attribute("INTENT");

    private static final Attribute INTENT_READ = INTENT.child("READ");

    private static final Attribute INTENT_WRITE = INTENT.child("WRITE");

    private static final Attribute RISK = new Attribute("RISK");

    private static final Attribute RISK_IRREVERSIBLE = RISK.child("IRREVERSIBLE");

    private static final Attribute RISK_PRIVILEGE = RISK.child("PRIVILEGE");

    private static final Attribute RISK_PRIVILEGE_ESCALATION = RISK_PRIVILEGE.child("ESCALATION");

    private static final AttributeAxis INTENT_AXIS = new AttributeAxis(INTENT, AttributeConstraint.REQUIRED_EXCLUSIVE);

    private static final AttributeAxis RISK_AXIS = new AttributeAxis(RISK, AttributeConstraint.OPTIONAL);

    @Test
    @DisplayName("Expands Root Anchors And Builds Sorted Leaf Index")
    void expandsRootAnchorsAndBuildsSortedLeafIndex() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        module("platform", 20),
                        module("account", 10)),
                List.of());

        assertEquals(
                List.of(TreeCode.ofSegment("ACCOUNT"), TreeCode.ofSegment("PLATFORM")),
                catalog.tree().stream().map(OperationNode::code).toList());
        assertEquals(
                List.of(
                        new OperationCode("ACCOUNT:ITEMS:READ"),
                        new OperationCode("ACCOUNT:ITEMS:MANAGE"),
                        new OperationCode("PLATFORM:ITEMS:READ"),
                        new OperationCode("PLATFORM:ITEMS:MANAGE")),
                catalog.leaves().stream().map(OperationNode::code).toList());
        assertEquals(TreeCode.ofColon("PLATFORM:ITEMS"), catalog.leafByCode("platform:items:read").parentCode());
        assertNull(catalog.tree().getFirst().parentCode());
        assertEquals(OperationNodeType.GROUP, catalog.tree().getFirst().type());
        assertEquals(OperationNodeType.LEAF, catalog.leafByCode("platform:items:read").type());
        assertFalse(catalog.tree().getFirst().type().grantable());
        assertTrue(catalog.leafByCode("platform:items:read").type().grantable());
    }

    @Test
    @DisplayName("Derives Full Codes And Parents Through Four Or More Levels")
    void derivesFullCodesAndParentsThroughDeepLevels() {
        OperationCatalog catalog = new OperationCatalog(List.of(deepModule()), List.of());

        OperationNode.Leaf deepest = catalog.leafByCode("orders:invoices:lines:taxes:read");
        assertEquals(new OperationCode("ORDERS:INVOICES:LINES:TAXES:READ"), deepest.code());
        assertEquals(TreeCode.ofColon("ORDERS:INVOICES:LINES:TAXES"), deepest.parentCode());
        assertEquals(
                TreeCode.ofColon("ORDERS:INVOICES:LINES"),
                tree(catalog, "ORDERS:INVOICES:LINES:TAXES").parentCode());
        assertEquals(TreeCode.ofColon("ORDERS"), tree(catalog, "ORDERS:INVOICES").parentCode());
        assertEquals(new Operation("ORDERS:INVOICES:LINES:TAXES:READ"), deepest.operation());
        assertEquals(TreeCode.ofSegment("READ"), deepest.code().verb());
    }

    @Test
    @DisplayName("Mixes Groups And Leaves Under One Parent In Sorted Order")
    void mixesGroupsAndLeavesUnderOneParent() {
        OperationCatalog catalog = new OperationCatalog(List.of(deepModule()), List.of());

        OperationNode.Group orders = catalog.tree().getFirst();

        assertEquals(
                List.of(
                        new OperationCode("ORDERS:EXPORT"),
                        TreeCode.ofColon("ORDERS:INVOICES"),
                        TreeCode.ofColon("ORDERS:ACCESS"),
                        TreeCode.ofColon("ORDERS:DRAFTS")),
                codes(orders));
        assertInstanceOf(OperationNode.Leaf.class, orders.children().getFirst());
        assertInstanceOf(OperationNode.Group.class, orders.children().get(1));
        assertEquals(List.of(), orders.children().getFirst().children());
        assertEquals(TreeCode.ofSegment("ORDERS"), orders.children().getFirst().parentCode());
    }

    @Test
    @DisplayName("Lists Leaves Depth First In Sibling Order")
    void listsLeavesDepthFirst() {
        OperationCatalog catalog = new OperationCatalog(List.of(deepModule()), List.of());

        List<OperationCode> expected = List.of(
                new OperationCode("ORDERS:EXPORT"),
                new OperationCode("ORDERS:INVOICES:READ"),
                new OperationCode("ORDERS:INVOICES:LINES:TAXES:READ"),
                new OperationCode("ORDERS:INVOICES:LINES:TAXES:UPDATE"),
                new OperationCode("ORDERS:INVOICES:LINES:READ"),
                new OperationCode("ORDERS:ACCESS:*"));
        assertEquals(expected, catalog.leaves().stream().map(OperationNode::code).toList());
        assertEquals(
                expected,
                catalog.tree().getFirst().descendantLeaves().stream().map(OperationNode::code).toList());
    }

    @Test
    @DisplayName("Declares Wildcard Leaf As Last Node Only")
    void declaresWildcardLeaf() {
        OperationCatalog catalog = new OperationCatalog(List.of(deepModule()), List.of());

        OperationNode.Leaf access = catalog.leafByCode("orders:access:*");

        assertTrue(access.code().isWildcard());
        assertEquals(TreeCode.ofSegment("*"), access.code().verb());
        assertEquals(TreeCode.ofColon("ORDERS:ACCESS"), access.parentCode());
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(TreeCode.ofSegment("*"), Display.of("operation.invalid"), 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(
                        TreeCode.ofSegment("ORDERS"),
                        Display.of("operation.orders"),
                        10,
                        OperationNode.group(TreeCode.ofSegment("*"), Display.of("operation.invalid"), 10)));
        assertThrows(IllegalArgumentException.class, () -> new Action("*"));
    }

    @Test
    @DisplayName("Allows A Leaf Directly Under The Root")
    void allowsLeafDirectlyUnderRoot() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        () -> OperationNode.root(
                                TreeCode.ofSegment("ORDERS"),
                                Display.of("operation.orders"),
                                10,
                                OperationNode.leaf(new Action("EXPORT"), Display.of("operation.orders.export"), 10))),
                List.of());

        assertEquals(
                List.of(new OperationCode("ORDERS:EXPORT")),
                catalog.leaves().stream().map(OperationNode::code).toList());
    }

    @Test
    @DisplayName("Projects Tree To Selected Operations")
    void projectsTreeToSelectedOperations() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        module("platform", 20),
                        module("account", 10)),
                List.of());

        List<OperationNode.Group> projected = catalog.tree(
                List.of(
                        new Operation("account:items:manage"),
                        new Operation("platform:items:read")));

        assertEquals(
                List.of(TreeCode.ofSegment("ACCOUNT"), TreeCode.ofSegment("PLATFORM")),
                projected.stream().map(OperationNode::code).toList());
        assertEquals(
                List.of(new OperationCode("ACCOUNT:ITEMS:MANAGE")),
                projected.get(0).children().getFirst().children().stream().map(OperationNode::code).toList());
        assertEquals(
                List.of(new OperationCode("PLATFORM:ITEMS:READ")),
                projected.get(1).children().getFirst().children().stream().map(OperationNode::code).toList());
    }

    @Test
    @DisplayName("Projection Prunes Empty Groups At Any Depth And Keeps Tree Order")
    void projectionPrunesDeepEmptyGroups() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(deepModule(), module("account", 5)),
                List.of());

        List<OperationNode.Group> projected = catalog.tree(
                List.of(
                        new Operation("orders:invoices:lines:read"),
                        new Operation("account:items:read")));

        assertEquals(
                List.of(TreeCode.ofSegment("ACCOUNT"), TreeCode.ofSegment("ORDERS")),
                projected.stream().map(OperationNode::code).toList());
        OperationNode.Group orders = projected.get(1);
        assertEquals(List.of(TreeCode.ofColon("ORDERS:INVOICES")), codes(orders));
        OperationNode.Group invoices = (OperationNode.Group) orders.children().getFirst();
        assertEquals(List.of(TreeCode.ofColon("ORDERS:INVOICES:LINES")), codes(invoices));
        OperationNode.Group lines = (OperationNode.Group) invoices.children().getFirst();
        assertEquals(List.of(new OperationCode("ORDERS:INVOICES:LINES:READ")), codes(lines));
        assertEquals(List.of(), catalog.tree(List.of()));
        assertEquals(
                List.of(new OperationCode("ORDERS:INVOICES:LINES:READ")),
                catalog.leaves(List.of(new Operation("orders:invoices:lines:read")))
                        .stream()
                        .map(OperationNode::code)
                        .toList());
    }

    @Test
    @DisplayName("Allows Same Local Codes In Different Roots")
    void allowsSameLocalCodesInDifferentRoots() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        module("project", 10),
                        module("inventory", 20)),
                List.of());

        assertEquals(
                List.of(
                        new OperationCode("PROJECT:ITEMS:READ"),
                        new OperationCode("PROJECT:ITEMS:MANAGE"),
                        new OperationCode("INVENTORY:ITEMS:READ"),
                        new OperationCode("INVENTORY:ITEMS:MANAGE")),
                catalog.leaves().stream().map(OperationNode::code).toList());
    }

    @Test
    @DisplayName("Rejects Invalid Or Duplicate Declarations")
    void rejectsInvalidOrDuplicateDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module("platform", 10), module("platform", 20)), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(TreeCode.ofSegment(" "), Display.of("operation.invalid"), 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(
                        TreeCode.ofSegment("PLATFORM"),
                        Display.of("operation.platform"),
                        10,
                        OperationNode.group(TreeCode.ofSegment("ROLES"), 10),
                        OperationNode.group(TreeCode.ofSegment("ROLES"), 20)));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(
                        TreeCode.ofSegment("PLATFORM"),
                        Display.of("operation.platform"),
                        10,
                        OperationNode.group(
                                TreeCode.ofSegment("ROLES"),
                                10,
                                OperationNode.leaf(Action.READ, 10),
                                OperationNode.leaf(Action.READ, 20))));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(TreeCode.ofColon("PLATFORM:ROLES"), Display.of("operation.platform"), 10));
        assertThrows(
                NullPointerException.class,
                () -> OperationNode.root(
                        TreeCode.ofSegment("PLATFORM"),
                        Display.of("operation.platform"),
                        10,
                        (OperationNode.Declaration) null));
    }

    @Test
    @DisplayName("Rejects A Group And A Leaf With The Same Name Under One Parent")
    void rejectsGroupAndLeafWithSameName() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(
                        TreeCode.ofSegment("ORDERS"),
                        Display.of("operation.orders"),
                        10,
                        OperationNode.group(TreeCode.ofSegment("EXPORT"), Display.of("operation.orders.export"), 10),
                        OperationNode.leaf(new Action("EXPORT"), Display.of("operation.orders.export.leaf"), 20)));

        assertTrue(failure.getMessage().contains("节点编码重复"));
        assertTrue(failure.getMessage().contains("ORDERS:EXPORT"));
    }

    @Test
    @DisplayName("Rejects Leaf Codes Longer Than 128 Characters")
    void rejectsLeafCodesLongerThanLimit() {
        TreeCode first = TreeCode.ofSegment("B".repeat(61));
        TreeCode second = TreeCode.ofSegment("C".repeat(61));

        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.root(
                        TreeCode.ofSegment("A"),
                        Display.of("operation.a"),
                        10,
                        OperationNode.group(
                                first,
                                Display.of("operation.a.b"),
                                10,
                                OperationNode.group(
                                        second,
                                        Display.of("operation.a.b.c"),
                                        10,
                                        OperationNode.leaf(Action.READ, Display.of("operation.a.b.c.read"), 10)))));
        OperationNode.Group fits = OperationNode.root(
                TreeCode.ofSegment("A"),
                Display.of("operation.a"),
                10,
                OperationNode.group(
                        TreeCode.ofSegment("B".repeat(60)),
                        Display.of("operation.a.b"),
                        10,
                        OperationNode.group(
                                TreeCode.ofSegment("C".repeat(60)),
                                Display.of("operation.a.b.c"),
                                10,
                                OperationNode.leaf(Action.READ, Display.of("operation.a.b.c.read"), 10))));
        assertEquals(128, fits.descendantLeaves().getFirst().code().format(NameFormat.COLON).length());
    }

    @Test
    @DisplayName("Rejects Unknown Operations In Lookup And Projection")
    void rejectsUnknownOperationsInLookupAndProjection() {
        OperationCatalog catalog = new OperationCatalog(List.of(module("platform", 10)), List.of());

        assertThrows(IllegalArgumentException.class, () -> catalog.leafByCode("platform:unknown:read"));
        assertEquals(Optional.empty(), catalog.leaf("platform:unknown:read"));
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
                        module("account", 10)),
                List.of());
        OperationCatalog presentationChanged = new OperationCatalog(
                List.of(
                        module("account", 200),
                        module("platform", 100)),
                List.of());
        OperationCatalog operationsChanged = new OperationCatalog(
                List.of(module("platform", 20)),
                List.of());

        assertEquals(first.fingerprint(), presentationChanged.fingerprint());
        assertNotEquals(first.fingerprint(), operationsChanged.fingerprint());
    }

    @Test
    @DisplayName("Fingerprint Keeps Its Known Digest")
    void fingerprintKeepsKnownDigest() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        module("platform", 20),
                        module("account", 10)),
                List.of());

        assertEquals("tLZRkP4wMCgrPDrZ-mVu1DYFEXaM93XtxGogLleV9ek", catalog.fingerprint());
    }

    @Test
    @DisplayName("Fingerprint Ignores Attributes")
    void fingerprintIgnoresAttributes() {
        OperationCatalog plain = new OperationCatalog(
                List.of(
                        module("platform", 20),
                        module("account", 10)),
                List.of());
        OperationCatalog attributed = new OperationCatalog(
                List.of(
                        attributedModule(
                                "platform",
                                OperationNode.leaf(Action.READ, 10, INTENT_WRITE, RISK_IRREVERSIBLE),
                                OperationNode.leaf(Action.MANAGE, 20, INTENT_READ)),
                        attributedModule(
                                "account",
                                OperationNode.leaf(Action.READ, 10, INTENT_READ),
                                OperationNode.leaf(Action.MANAGE, 20, INTENT_WRITE))),
                List.of(INTENT_AXIS, RISK_AXIS));

        assertEquals(plain.fingerprint(), attributed.fingerprint());
    }

    @Test
    @DisplayName("Rejects Leaf Missing Required Exclusive Axis")
    void rejectsLeafMissingRequiredExclusiveAxis() {
        OperationModule module = attributedModule(
                "account",
                OperationNode.leaf(Action.READ, 10, RISK_IRREVERSIBLE));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module), List.of(INTENT_AXIS, RISK_AXIS)));

        assertTrue(failure.getMessage().contains("ACCOUNT:ITEMS:READ"));
        assertTrue(failure.getMessage().contains("INTENT"));
        assertTrue(failure.getMessage().contains("叶子"));
    }

    @Test
    @DisplayName("Rejects Exclusive Conflict")
    void rejectsExclusiveConflict() {
        OperationModule module = attributedModule(
                "account",
                OperationNode.leaf(Action.READ, 10, INTENT_READ, INTENT_WRITE));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module), List.of(INTENT_AXIS)));

        assertTrue(failure.getMessage().contains("ACCOUNT:ITEMS:READ"));
        assertTrue(failure.getMessage().contains("INTENT"));
    }

    @Test
    @DisplayName("Allows Multiple Attributes On Optional Axis")
    void allowsMultipleOnOptionalAxis() {
        OperationModule module = attributedModule(
                "account",
                OperationNode.leaf(Action.READ, 10, INTENT_READ),
                OperationNode.leaf(
                        Action.MANAGE,
                        20,
                        INTENT_WRITE,
                        RISK_IRREVERSIBLE,
                        RISK_PRIVILEGE_ESCALATION));

        OperationCatalog catalog = new OperationCatalog(List.of(module), List.of(INTENT_AXIS, RISK_AXIS));

        assertEquals(
                Set.of(RISK_IRREVERSIBLE, RISK_PRIVILEGE_ESCALATION),
                catalog.leafByCode("account:items:manage").get(RISK_AXIS));
        assertEquals(Set.of(), catalog.leafByCode("account:items:read").get(RISK_AXIS));
        assertEquals(Optional.empty(), catalog.leafByCode("account:items:read").only(RISK_AXIS));
    }

    @Test
    @DisplayName("Rejects Attribute Without Registered Axis")
    void rejectsAttributeWithoutAxis() {
        OperationModule module = attributedModule(
                "account",
                OperationNode.leaf(Action.READ, 10, INTENT_READ, RISK_IRREVERSIBLE));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module), List.of(INTENT_AXIS)));

        assertTrue(failure.getMessage().contains("属性未登记轴"));
        assertTrue(failure.getMessage().contains("RISK:IRREVERSIBLE"));
    }

    @Test
    @DisplayName("Rejects Axis Root Used As Attribute Value")
    void rejectsAxisRootUsedAsValue() {
        OperationModule module = attributedModule(
                "account",
                OperationNode.leaf(Action.READ, 10, INTENT));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module), List.of(INTENT_AXIS)));

        assertTrue(failure.getMessage().contains("属性不能直接使用轴根"));
    }

    @Test
    @DisplayName("Rejects Duplicate Axis Root")
    void rejectsDuplicateAxisRoot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(
                        List.of(module("account", 10)),
                        List.of(INTENT_AXIS, new AttributeAxis(INTENT, AttributeConstraint.OPTIONAL))));
    }

    @Test
    @DisplayName("Rejects Nested Axis Roots")
    void rejectsNestedAxisRoots() {
        AttributeAxis nested = new AttributeAxis(RISK_PRIVILEGE, AttributeConstraint.OPTIONAL);

        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module("account", 10)), List.of(RISK_AXIS, nested)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module("account", 10)), List.of(nested, RISK_AXIS)));
    }

    @Test
    @DisplayName("Rejects Null Axis Or Axis Collection")
    void rejectsNullAxisOrCollection() {
        List<AttributeAxis> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> new OperationCatalog(List.of(module("account", 10)), null));
        assertThrows(
                NullPointerException.class,
                () -> new OperationCatalog(List.of(module("account", 10)), withNull));
    }

    @Test
    @DisplayName("Rejects Duplicate Attribute On Leaf")
    void rejectsDuplicateAttributeOnLeaf() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationNode.leaf(Action.READ, 10, INTENT_READ, INTENT_READ));
        assertThrows(
                NullPointerException.class,
                () -> OperationNode.leaf(Action.READ, 10, INTENT_READ, null));
    }

    @Test
    @DisplayName("Lists Leaves With Exactly The Attribute")
    void leavesWithExactOnly() {
        OperationCatalog catalog = attributedCatalog();

        assertEquals(
                List.of(new OperationCode("ACCOUNT:ITEMS:MANAGE"), new OperationCode("PLATFORM:ITEMS:READ")),
                catalog.leavesWith(RISK_PRIVILEGE_ESCALATION).stream().map(OperationNode::code).toList());
        assertEquals(List.of(), catalog.leavesWith(RISK_PRIVILEGE));
        assertEquals(List.of(), catalog.leavesWith(RISK));
    }

    @Test
    @DisplayName("Lists Leaves Under The Attribute In Catalog Order")
    void leavesUnderIncludesDescendantsInCatalogOrder() {
        OperationCatalog catalog = attributedCatalog();

        assertEquals(
                List.of(
                        new OperationCode("ACCOUNT:ITEMS:READ"),
                        new OperationCode("ACCOUNT:ITEMS:MANAGE"),
                        new OperationCode("PLATFORM:ITEMS:READ")),
                catalog.leavesUnder(RISK).stream().map(OperationNode::code).toList());
        assertEquals(
                List.of(new OperationCode("ACCOUNT:ITEMS:MANAGE"), new OperationCode("PLATFORM:ITEMS:READ")),
                catalog.leavesUnder(RISK_PRIVILEGE).stream().map(OperationNode::code).toList());
        assertEquals(
                List.of(new OperationCode("ACCOUNT:ITEMS:READ"), new OperationCode("PLATFORM:ITEMS:READ")),
                catalog.leavesUnder(RISK_IRREVERSIBLE).stream().map(OperationNode::code).toList());
    }

    @Test
    @DisplayName("Leaf Answers has hasUnder get And only")
    void hasHasUnderGetOnly() {
        OperationCatalog catalog = attributedCatalog();
        OperationNode.Leaf manage = catalog.leafByCode("account:items:manage");
        OperationNode.Leaf platformRead = catalog.leafByCode("platform:items:read");

        assertTrue(manage.has(INTENT_WRITE));
        assertFalse(manage.has(RISK_PRIVILEGE));
        assertTrue(manage.hasUnder(RISK_PRIVILEGE));
        assertTrue(manage.hasUnder(RISK_PRIVILEGE_ESCALATION));
        assertFalse(manage.hasUnder(RISK_IRREVERSIBLE));
        assertEquals(Set.of(INTENT_WRITE), manage.get(INTENT_AXIS));
        assertEquals(Optional.of(INTENT_WRITE), manage.only(INTENT_AXIS));
        assertEquals(Optional.of(RISK_PRIVILEGE_ESCALATION), manage.only(RISK_AXIS));
        assertEquals(Optional.of(RISK_IRREVERSIBLE), catalog.leafByCode("account:items:read").only(RISK_AXIS));
        assertEquals(Set.of(RISK_IRREVERSIBLE, RISK_PRIVILEGE_ESCALATION), platformRead.get(RISK_AXIS));
        assertThrows(IllegalStateException.class, () -> platformRead.only(RISK_AXIS));
    }

    @Test
    @DisplayName("Sorted And Retaining Projections Keep Attributes")
    void sortedAndRetainingKeepAttributes() {
        OperationCatalog catalog = attributedCatalog();

        OperationNode.Leaf sorted = catalog.tree()
                .stream()
                .flatMap(root -> root.descendantLeaves().stream())
                .filter(leaf -> leaf.code().equals(new OperationCode("ACCOUNT:ITEMS:MANAGE")))
                .findFirst()
                .orElseThrow();
        OperationNode.Leaf retained = catalog
                .tree(List.of(new Operation("account:items:manage")))
                .getFirst()
                .descendantLeaves()
                .getFirst();

        assertEquals(Set.of(INTENT_WRITE, RISK_PRIVILEGE_ESCALATION), sorted.attributes());
        assertEquals(Set.of(INTENT_WRITE, RISK_PRIVILEGE_ESCALATION), retained.attributes());
        assertEquals(catalog.axes(), List.of(INTENT_AXIS, RISK_AXIS));
    }

    @Test
    @DisplayName("Leaf Declares Required Leaves And Keeps Them Through Projection")
    void leafDeclaresRequiredLeaves() {
        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        requiresModule(
                                OperationNode.leaf(Action.READ, 10),
                                OperationNode.leaf(Action.UPDATE, 20)
                                        .requires(
                                                new OperationCode("ORDERS:ITEMS:READ"),
                                                new OperationCode("ORDERS:ITEMS:READ")),
                                OperationNode.wildcardLeaf(30)
                                        .requires(new OperationCode("ORDERS:ITEMS:UPDATE")))),
                List.of());

        assertEquals(List.of(), catalog.leafByCode("orders:items:read").requires());
        assertEquals(
                List.of(new OperationCode("ORDERS:ITEMS:READ")),
                catalog.leafByCode("orders:items:update").requires());
        assertEquals(
                List.of(new OperationCode("ORDERS:ITEMS:UPDATE")),
                catalog.leafByCode("orders:items:*").requires());
        assertThrows(
                UnsupportedOperationException.class,
                () -> catalog.leafByCode("orders:items:update").requires().add(new OperationCode("ORDERS:ITEMS:READ")));
        assertEquals(
                List.of(new OperationCode("ORDERS:ITEMS:READ")),
                catalog.tree(List.of(new Operation("orders:items:update")))
                        .getFirst()
                        .descendantLeaves()
                        .getFirst()
                        .requires());
        assertThrows(
                NullPointerException.class,
                () -> OperationNode.leaf(Action.READ, 10).requires((OperationCode) null));
    }

    @Test
    @DisplayName("Rejects Required Leaf That Is Missing Or A Group")
    void rejectsMissingRequiredLeaf() {
        OperationModule module = requiresModule(
                OperationNode.leaf(Action.UPDATE, 20).requires(new OperationCode("ORDERS:ITEMS:READ")));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module), List.of()));

        assertTrue(failure.getMessage().contains("ORDERS:ITEMS:UPDATE"));
        assertTrue(failure.getMessage().contains("ORDERS:ITEMS:READ"));
    }

    @Test
    @DisplayName("Rejects Leaf Requiring Itself")
    void rejectsSelfRequirement() {
        OperationModule module = requiresModule(
                OperationNode.leaf(Action.READ, 10).requires(new OperationCode("ORDERS:ITEMS:READ")));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(module), List.of()));

        assertTrue(failure.getMessage().contains("依赖自身"));
        assertTrue(failure.getMessage().contains("ORDERS:ITEMS:READ"));
    }

    @Test
    @DisplayName("Rejects Requirement Cycles And Allows Shared Requirements")
    void rejectsRequirementCycle() {
        OperationModule cycle = requiresModule(
                OperationNode.leaf(Action.READ, 10).requires(new OperationCode("ORDERS:ITEMS:MANAGE")),
                OperationNode.leaf(Action.UPDATE, 20).requires(new OperationCode("ORDERS:ITEMS:READ")),
                OperationNode.leaf(Action.MANAGE, 30).requires(new OperationCode("ORDERS:ITEMS:UPDATE")));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OperationCatalog(List.of(cycle), List.of()));

        assertTrue(failure.getMessage().contains("成环"));
        assertTrue(failure.getMessage().contains("ORDERS:ITEMS:READ"));
        assertTrue(failure.getMessage().contains("ORDERS:ITEMS:MANAGE"));

        OperationModule diamond = requiresModule(
                OperationNode.leaf(Action.READ, 10),
                OperationNode.leaf(Action.UPDATE, 20).requires(new OperationCode("ORDERS:ITEMS:READ")),
                OperationNode.leaf(Action.MANAGE, 30)
                        .requires(new OperationCode("ORDERS:ITEMS:READ"), new OperationCode("ORDERS:ITEMS:UPDATE")));
        assertEquals(3, new OperationCatalog(List.of(diamond), List.of()).leaves().size());
    }

    @Test
    @DisplayName("Fingerprint Ignores Required Leaves")
    void fingerprintIgnoresRequires() {
        OperationCatalog plain = new OperationCatalog(
                List.of(
                        requiresModule(
                                OperationNode.leaf(Action.READ, 10),
                                OperationNode.leaf(Action.UPDATE, 20))),
                List.of());
        OperationCatalog required = new OperationCatalog(
                List.of(
                        requiresModule(
                                OperationNode.leaf(Action.READ, 10),
                                OperationNode.leaf(Action.UPDATE, 20)
                                        .requires(new OperationCode("ORDERS:ITEMS:READ")))),
                List.of());

        assertEquals(plain.fingerprint(), required.fingerprint());
    }

    /** @return 在 ORDERS:ITEMS 下声明给定叶子的测试 Operation 模块 */
    private static OperationModule requiresModule(OperationNode.Declaration... leaves) {
        return () -> OperationNode.root(
                TreeCode.ofSegment("ORDERS"),
                Display.of("operation.orders"),
                10,
                OperationNode.group(TreeCode.ofSegment("ITEMS"), Display.of("operation.orders.items"), 10, leaves));
    }

    /** @return 在整棵树中按编码文本查找的分组 */
    private static OperationNode.Group tree(OperationCatalog catalog, String code) {
        List<OperationNode.Group> pending = new ArrayList<>(catalog.tree());
        while (!pending.isEmpty()) {
            OperationNode.Group group = pending.removeFirst();
            if (group.code().equals(TreeCode.ofColon(code))) {
                return group;
            }
            group.children()
                    .stream()
                    .filter(OperationNode.Group.class::isInstance)
                    .map(OperationNode.Group.class::cast)
                    .forEach(pending::add);
        }
        throw new AssertionError("目录中没有分组 " + code);
    }

    /** @return 分组直接子节点的编码，保持子节点顺序 */
    private static List<TreeCode> codes(OperationNode.Group group) {
        return group.children().stream().map(OperationNode::code).toList();
    }

    /**
     * @return 根分组 ORDERS 的深层测试目录：同一父分组下混合叶子、分组和通配叶子，
     *         声明顺序故意与排序值相反，另含一个没有任何叶子的深层空分组
     */
    private static OperationModule deepModule() {
        return () -> OperationNode.root(
                TreeCode.ofSegment("ORDERS"),
                Display.of("operation.orders"),
                10,
                OperationNode.group(
                        TreeCode.ofSegment("DRAFTS"),
                        Display.of("operation.orders.drafts"),
                        60,
                        OperationNode.group(
                                TreeCode.ofSegment("ARCHIVE"),
                                Display.of("operation.orders.drafts.archive"),
                                10)),
                OperationNode.group(
                        TreeCode.ofSegment("ACCESS"),
                        Display.of("operation.orders.access"),
                        50,
                        OperationNode.wildcardLeaf(Display.of("operation.orders.access.all"), 10)),
                OperationNode.group(
                        TreeCode.ofSegment("INVOICES"),
                        Display.of("operation.orders.invoices"),
                        20,
                        OperationNode.group(
                                TreeCode.ofSegment("LINES"),
                                Display.of("operation.orders.invoices.lines"),
                                20,
                                OperationNode.leaf(Action.READ, Display.of("operation.orders.invoices.lines.read"), 20),
                                OperationNode.group(
                                        TreeCode.ofSegment("TAXES"),
                                        Display.of("operation.orders.invoices.lines.taxes"),
                                        10,
                                        OperationNode.leaf(
                                                Action.UPDATE,
                                                Display.of("operation.orders.invoices.lines.taxes.update"),
                                                20),
                                        OperationNode.leaf(
                                                Action.READ,
                                                Display.of("operation.orders.invoices.lines.taxes.read"),
                                                10))),
                        OperationNode.leaf(Action.READ, Display.of("operation.orders.invoices.read"), 10)),
                OperationNode.leaf(new Action("EXPORT"), Display.of("operation.orders.export"), 5));
    }

    /** @return 带两条轴和三个叶子的属性测试目录 */
    private static OperationCatalog attributedCatalog() {
        return new OperationCatalog(
                List.of(
                        attributedModule(
                                "platform",
                                OperationNode.leaf(
                                        Action.READ,
                                        10,
                                        INTENT_READ,
                                        RISK_IRREVERSIBLE,
                                        RISK_PRIVILEGE_ESCALATION)),
                        attributedModule(
                                "account",
                                OperationNode.leaf(Action.READ, 10, INTENT_READ, RISK_IRREVERSIBLE),
                                OperationNode.leaf(
                                        Action.MANAGE,
                                        20,
                                        INTENT_WRITE,
                                        RISK_PRIVILEGE_ESCALATION))),
                List.of(INTENT_AXIS, RISK_AXIS));
    }

    /** @return 声明相同 ITEMS 分组叶子的测试 Operation 模块，排序值不同时按 sortOrder 排序 */
    private static OperationModule attributedModule(String anchor, OperationNode.Declaration... leaves) {
        int sortOrder = "account".equals(anchor) ? 10 : 20;
        return () -> OperationNode.root(
                TreeCode.ofSegment(anchor),
                Display.of("operation." + anchor),
                sortOrder,
                OperationNode
                        .group(TreeCode.ofSegment("ITEMS"), Display.of("operation." + anchor + ".items"), 10, leaves));
    }

    /** @return 声明具有相同 ITEMS 分组叶子的测试 Operation 模块 */
    private static OperationModule module(String anchor, int sortOrder) {
        return () -> OperationNode.root(
                TreeCode.ofSegment(anchor),
                Display.of("operation." + anchor),
                sortOrder,
                OperationNode.group(
                        TreeCode.ofSegment("ITEMS"),
                        Display.of("operation." + anchor + ".items"),
                        10,
                        OperationNode.leaf(Action.READ, Display.of("operation." + anchor + ".items.read"), 10),
                        OperationNode.leaf(Action.MANAGE, Display.of("operation." + anchor + ".items.manage"), 20)));
    }

    @Test
    @DisplayName("Derives Display Keys From Codes And Honors Explicit Overrides")
    void derivesDisplayKeysFromCodesAndHonorsOverrides() {
        OperationNode.Group root = OperationNode.root(
                TreeCode.ofSegment("ORDERS"),
                10,
                OperationNode.group(
                        TreeCode.ofSegment("INVOICE_LINES"),
                        10,
                        OperationNode.leaf(Action.READ, 10),
                        OperationNode.leaf(Action.UPDATE, Display.of("custom.update"), 20)),
                OperationNode.group(
                        TreeCode.ofSegment("ACCESS"),
                        20,
                        OperationNode.wildcardLeaf(10)));

        assertEquals("operation.orders", root.display().nameKey());
        OperationNode.Group lines = (OperationNode.Group) root.children().getFirst();
        assertEquals("operation.orders.invoice-lines", lines.display().nameKey());
        assertEquals("operation.orders.invoice-lines.read", lines.children().get(0).display().nameKey());
        assertEquals(
                "operation.orders.invoice-lines.read.description",
                lines.children().get(0).display().descriptionKey());
        assertEquals("custom.update", lines.children().get(1).display().nameKey());
        assertEquals("custom.update.description", lines.children().get(1).display().descriptionKey());
        assertEquals("operation.orders.access.all", root.children().get(1).children().getFirst().display().nameKey());
        assertThrows(IllegalArgumentException.class, () -> Display.of(" "));
    }
}
