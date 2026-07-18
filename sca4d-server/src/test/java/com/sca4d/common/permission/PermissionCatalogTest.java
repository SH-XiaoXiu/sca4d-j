package com.sca4d.common.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证权限目录扫描、父子关系和重复编码检测。 */
class PermissionCatalogTest {

    @Test
    void shouldBuildSortedTreeAndFlatItems() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(
                () -> PermissionNode.module("demo", "permission.demo", 20,
                        PermissionNode.resource("demo:orders", "permission.demo.orders", 20,
                                PermissionNode.action("demo:orders:update", "permission.demo.orders.update", 200),
                                PermissionNode.action("demo:orders:read", "permission.demo.orders.read", 100))),
                () -> PermissionNode.module("alpha", "permission.alpha", 10,
                        PermissionNode.resource("alpha:users", "permission.alpha.users", 100,
                                PermissionNode.action("alpha:users:read", "permission.alpha.users.read", 100)))));

        assertThat(catalog.tree()).extracting(PermissionNode::getCode).containsExactly("alpha", "demo");
        assertThat(catalog.tree().get(1).getChildren().getFirst().getChildren())
                .extracting(PermissionNode::getCode)
                .containsExactly("demo:orders:read", "demo:orders:update");
        assertThat(catalog.items())
                .extracting(PermissionItem::getCode)
                .containsExactly(
                        "alpha",
                        "alpha:users",
                        "alpha:users:read",
                        "demo",
                        "demo:orders",
                        "demo:orders:read",
                        "demo:orders:update");
        assertThat(catalog.items())
                .extracting(PermissionItem::getNameKey)
                .contains("permission.alpha", "permission.demo.orders.read");
    }

    @Test
    void shouldRejectDuplicatedCode() {
        assertThatThrownBy(() -> new PermissionCatalog(List.of(
                () -> PermissionNode.module("demo", "permission.demo", 100),
                () -> PermissionNode.module("demo", "permission.duplicate", 200))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复权限编码");
    }

    @Test
    void shouldRejectBlankCodeAndNameKey() {
        assertThatThrownBy(() -> new PermissionCatalog(List.of(() -> PermissionNode.module(" ", "permission.demo", 100))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限编码不能为空");

        assertThatThrownBy(() -> new PermissionCatalog(List.of(() -> PermissionNode.module("demo", " ", 100))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限名称消息键不能为空");
    }

    @Test
    void shouldRejectChildCodeWithoutParentPrefix() {
        assertThatThrownBy(() -> new PermissionCatalog(List.of(() -> PermissionNode.module("demo", "permission.demo", 100,
                PermissionNode.resource("orders", "permission.demo.orders", 100)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("子权限编码必须以父权限编码为前缀");
    }

    @Test
    void shouldAllowDottedAccountSecurityPermissionsBelowAccountModule() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(() -> PermissionNode.module(
                "account",
                "permission.account",
                100,
                PermissionNode.resource("account.security", "permission.account.security", 100,
                        PermissionNode.action(
                                "account.security.read", "permission.account.security.read", 100)))));

        assertThat(catalog.items()).extracting(PermissionItem::getCode)
                .containsExactly("account", "account.security", "account.security.read");
    }

    @Test
    void shouldRejectActionChildren() {
        assertThatThrownBy(() -> new PermissionCatalog(List.of(() -> PermissionNode.module("demo", "permission.demo", 100,
                new PermissionNode("demo:orders:read", "permission.demo.orders.read", PermissionType.ACTION, 100,
                        List.of(PermissionNode.action(
                                "demo:orders:read:extra", "permission.demo.orders.read.extra", 100)))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("动作权限不能包含子节点");
    }

    @Test
    void shouldRejectNonModuleRoot() {
        assertThatThrownBy(() -> new PermissionCatalog(List.of(
                () -> PermissionNode.resource("demo:orders", "permission.demo.orders", 100))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限根节点必须是模块");
    }
}
