package com.sca4d.iam.rbac;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.Display;
import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class PermissionSelectionRegistryTest {

    private static final RbacDomain PLATFORM = new RbacDomain("PLATFORM");

    private static final RbacDomain PROJECT = new RbacDomain("PROJECT");

    @Test
    @DisplayName("Builds Domain Selections From Leaf Grantable Attributes")
    void buildsDomainSelectionsFromLeafGrantableAttributes() {
        PermissionSelectionRegistry registry = new PermissionSelectionRegistry(projectCatalog());

        assertThat(registry.selection(PLATFORM).operations())
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("CATALOG:ITEMS:READ");
        assertThat(registry.selection(PROJECT).operations())
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("CATALOG:ITEMS:MANAGE", "CATALOG:ITEMS:READ");
        assertThat(registry.domains()).containsExactlyInAnyOrder(PLATFORM, PROJECT);
    }

    @Test
    @DisplayName("Rejects Internal Selector And Malformed Text From Role Management API")
    void rejectsInternalSelectorAndMalformedTextFromRoleManagementApi() {
        PermissionSelectionRegistry registry = new PermissionSelectionRegistry(projectCatalog());
        PermissionSelections selections = registry.selection(PROJECT);

        for (String text : List.of("*", "CATALOG", "CATALOG:*:READ", "CATALOG:ITEMS:UNKNOWN")) {
            assertThatThrownBy(() -> selections.validateGrantable(List.of(text)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(RbacErrorCode.OPERATION_INVALID));
        }
        assertThat(selections.validateGrantable(List.of("catalog:items:read")))
                .containsExactly(new PermissionSelector("CATALOG:ITEMS:READ"));
    }

    @Test
    @DisplayName("Rejects Leaf Without Grantable Domain")
    void rejectsLeafWithoutGrantableDomain() {
        OperationModule module = () -> tree(new Attribute[0], new Attribute[0]);

        assertThatThrownBy(() -> new PermissionSelectionRegistry(catalog(module)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有声明任何可授予的 RBAC 领域");
    }

    @Test
    @DisplayName("Rejects Grantable Attribute That Is Not A Single Domain Value")
    void rejectsGrantableAttributeThatIsNotSingleDomainValue() {
        Attribute nested = RbacGrantable.of(PLATFORM).child("SCOPE");
        OperationModule module = () -> tree(new Attribute[] {RbacGrantable.of(PLATFORM)}, new Attribute[] {nested});

        assertThatThrownBy(() -> new PermissionSelectionRegistry(catalog(module)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GRANTABLE:<domain>");
    }

    /**
     * @return READ 叶子授予 Platform 和 Project 域、MANAGE 叶子只授予 Project 域的测试目录
     */
    private static OperationCatalog projectCatalog() {
        OperationModule module = () -> tree(
                new Attribute[] {RbacGrantable.of(PROJECT)},
                new Attribute[] {RbacGrantable.of(PLATFORM), RbacGrantable.of(PROJECT)});
        return catalog(module);
    }

    private static OperationCatalog catalog(OperationModule module) {
        return new OperationCatalog(List.of(module), List.of(RbacGrantable.GRANTABLE));
    }

    /**
     * @param manageAttributes MANAGE 叶子声明的属性
     * @param readAttributes READ 叶子声明的属性
     * @return 包含 read 和 manage 的测试 Operation 树
     */
    private static OperationNode.Group tree(Attribute[] manageAttributes, Attribute[] readAttributes) {
        return OperationNode.root(
                TreeCode.ofSegment("CATALOG"),
                Display.of("operation.catalog"),
                100,
                OperationNode.group(
                        TreeCode.ofSegment("ITEMS"),
                        Display.of("operation.catalog.items"),
                        100,
                        OperationNode.leaf(
                                Action.MANAGE,
                                Display.of("operation.catalog.items.manage"),
                                200,
                                manageAttributes),
                        OperationNode
                                .leaf(Action.READ, Display.of("operation.catalog.items.read"), 100, readAttributes)));
    }
}
