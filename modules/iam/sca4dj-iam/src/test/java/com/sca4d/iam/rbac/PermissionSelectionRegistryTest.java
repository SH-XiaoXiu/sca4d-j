package com.sca4d.iam.rbac;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class PermissionSelectionRegistryTest {

    private static final RbacDomain PLATFORM = new RbacDomain("PLATFORM");

    private static final RbacDomain PROJECT = new RbacDomain("PROJECT");

    @Test
    @DisplayName("Builds Domain Selections From Module Behavior")
    void buildsDomainSelectionsFromModuleBehavior() {
        RbacOperationModule module = projectModule();
        PermissionSelectionRegistry registry = new PermissionSelectionRegistry(
                new OperationCatalog(List.of(module)),
                List.of(module));

        assertThat(registry.selection(PLATFORM).operations())
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("PROJECTS:ITEMS:READ");
        assertThat(registry.selection(PROJECT).operations())
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("PROJECTS:ITEMS:MANAGE", "PROJECTS:ITEMS:READ");
        assertThat(registry.domains()).containsExactlyInAnyOrder(PLATFORM, PROJECT);
    }

    @Test
    @DisplayName("Rejects Catalog Module Without Rbac Domain Behavior")
    void rejectsCatalogModuleWithoutRbacDomainBehavior() {
        OperationModule module = PermissionSelectionRegistryTest::tree;

        assertThatThrownBy(
                () -> new PermissionSelectionRegistry(
                        new OperationCatalog(List.of(module)),
                        List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing=[PROJECTS]");
    }

    @Test
    @DisplayName("Rejects Operation That Cannot Be Granted To Any Declared Domain")
    void rejectsOperationThatCannotBeGrantedToAnyDeclaredDomain() {
        RbacOperationModule module = new RbacOperationModule() {
            @Override
            public OperationNode.@NotNull Module operationTree() {
                return tree();
            }

            @Override
            public @NotNull Set<@NotNull RbacDomain> domains() {
                return Set.of(PLATFORM);
            }

            @Override
            public boolean canGrant(@NotNull RbacDomain domain, @NotNull Operation operation) {
                return false;
            }
        };

        assertThatThrownBy(
                () -> new PermissionSelectionRegistry(
                        new OperationCatalog(List.of(module)),
                        List.of(module)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能授予");
    }

    /**
     * @return 同时向 Platform 和 Project 提供不同 Operation 的测试模块
     */
    private static RbacOperationModule projectModule() {
        return new RbacOperationModule() {
            @Override
            public OperationNode.@NotNull Module operationTree() {
                return tree();
            }

            @Override
            public @NotNull Set<@NotNull RbacDomain> domains() {
                return Set.of(PLATFORM, PROJECT);
            }

            @Override
            public boolean canGrant(@NotNull RbacDomain domain, @NotNull Operation operation) {
                return PROJECT.equals(domain) || operation.code().format(COLON).endsWith(":READ");
            }
        };
    }

    /**
     * @return 包含 read 和 manage 的测试 Operation 树
     */
    private static OperationNode.Module tree() {
        return OperationNode.module(
                TreeCode.ofSegment("PROJECTS"),
                "operation.projects",
                100,
                OperationNode.resource(
                        TreeCode.ofSegment("ITEMS"),
                        "operation.projects.items",
                        100,
                        OperationNode.action(Action.MANAGE.code(), "operation.projects.items.manage", 200),
                        OperationNode.action(Action.READ.code(), "operation.projects.items.read", 100)));
    }
}
