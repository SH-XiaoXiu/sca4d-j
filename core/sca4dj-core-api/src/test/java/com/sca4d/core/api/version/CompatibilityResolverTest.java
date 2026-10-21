package com.sca4d.core.api.version;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证两类入口共享的契约选择和范围边界。 */
class CompatibilityResolverTest {
    @Test
    void selectsHighestImplementedContractAndExplicitRoute() {
        var resolver = new CompatibilityResolver(
                List.of(rule(1, CompatibilityStatus.DEPRECATED), rule(2, CompatibilityStatus.SUPPORTED)));
        var automatic = new ClientVersion("app", "1.10.0", "batch", "build", null);
        assertEquals(2, resolver.resolve(automatic, "projects", null).contract());
        assertEquals(1, resolver.resolve(automatic, "projects", 1).contract());
        assertEquals(CompatibilityStatus.DEPRECATED, resolver.resolve(automatic, "projects", 1).status());
        var explicit = new ClientVersion("app", "1.10.0", "batch", "build", 2);
        assertEquals("contract-conflict", resolver.resolve(explicit, "projects", 1).reason());
        assertEquals(2, resolver.resolve(explicit, "projects", null).contract());
    }

    @Test
    void rejectsUnknownClientsUnsupportedContractsAndUpperBoundary() {
        var resolver = new CompatibilityResolver(List.of(rule(1, CompatibilityStatus.SUPPORTED)));
        for (var client : List.of(
                new ClientVersion("app", "2.0.0", null, null, 1),
                new ClientVersion("app", "dev-abc123", null, null, 1),
                new ClientVersion("app", "feature-x", null, null, 1),
                new ClientVersion("app", "1.2", null, null, 1),
                new ClientVersion("other", "1.10.0", null, null, 1),
                new ClientVersion("app", "1.10.0", null, null, 3)))
            assertEquals(CompatibilityStatus.BLOCKED, resolver.resolve(client, "projects", null).status());
        assertEquals(
                1,
                resolver.resolve(new ClientVersion("app", "1.2.0", null, null, 1), "projects", null).contract());
    }

    @Test
    void rejectsOverlappingRulesAndBlocksExplicitlyDisabledContract() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompatibilityResolver(
                        List.of(rule(1, CompatibilityStatus.SUPPORTED), rule(1, CompatibilityStatus.DEPRECATED))));
        var resolver = new CompatibilityResolver(List.of(rule(1, CompatibilityStatus.BLOCKED)));
        var result = resolver.resolve(new ClientVersion("app", "1.3.0", null, null, 1), "projects", null);
        assertEquals(CompatibilityStatus.BLOCKED, result.status());
        assertThrows(IllegalStateException.class, result::contract);
    }

    @Test
    void allowsOpaqueVersionsOnlyWhenNoReleaseRangeIsDeclared() {
        var rule = new CompatibilityRule(
                "app",
                "projects",
                null,
                null,
                1,
                CompatibilityStatus.SUPPORTED,
                "v1",
                "/projects");
        var resolver = new CompatibilityResolver(List.of(rule));
        assertEquals(
                1,
                resolver.resolve(
                        new ClientVersion("app", "dev-abc123", null, null, 1),
                        "projects",
                        null).contract());
        assertEquals(
                1,
                resolver.resolve(
                        new ClientVersion("app", "feature-x", null, null, 1),
                        "projects",
                        null).contract());
        assertEquals(
                CompatibilityStatus.BLOCKED,
                resolver.resolve(
                        new ClientVersion("app", "dev-abc123", null, null, 2),
                        "projects",
                        null).status());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompatibilityRule(
                        "app",
                        "projects",
                        "1.0.0",
                        null,
                        1,
                        CompatibilityStatus.SUPPORTED,
                        "v1",
                        "/projects"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompatibilityResolver(
                        List.of(rule, rule(1, CompatibilityStatus.SUPPORTED))));
    }

    private static CompatibilityRule rule(int contract, CompatibilityStatus status) {
        return new CompatibilityRule(
                "app",
                "projects",
                "1.2.0",
                "2.0.0",
                contract,
                status,
                "projects-v" + contract,
                "/v" + contract + "/projects");
    }
}
