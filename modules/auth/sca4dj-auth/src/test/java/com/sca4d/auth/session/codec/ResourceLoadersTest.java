package com.sca4d.auth.session.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.auth.exception.AuthException;
import com.sca4d.core.api.authentication.principalsession.ResourceLoader;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ClassCanBeRecord")
class ResourceLoadersTest {

    private static final Principal PRINCIPAL = new TestPrincipal(1);

    @Test
    @DisplayName("Returns Empty Resource Views When No Loader Is Installed")
    void returnsEmptyResourceViewsWhenNoLoaderIsInstalled() {
        Map<Long, List<Resource>> resources = new ResourceLoaders(List.of(), Runnable::run)
                .load(List.of(PRINCIPAL));

        assertThat(resources).containsOnlyKeys(PRINCIPAL.principalId());
        assertThat(resources.get(PRINCIPAL.principalId())).isEmpty();
    }

    @Test
    @DisplayName("Merges Multiple Contributors For The Same Principal")
    void mergesMultipleContributorsForTheSamePrincipal() {
        ResourceLoader operations = principals -> Map.of(
                PRINCIPAL.principalId(),
                List.of(new Operation("platform:account:read")));
        ResourceLoader businessResources = principals -> Map.of(
                PRINCIPAL.principalId(),
                List.of(new TestResource("tenant", "42")));

        Map<Long, List<Resource>> resources = new ResourceLoaders(
                List.of(operations, businessResources),
                Runnable::run)
                .load(List.of(PRINCIPAL));

        assertThat(resources.get(PRINCIPAL.principalId()))
                .extracting(resource -> resource.type() + "/" + resource.id())
                .containsExactly("CORE:OPERATIONS/PLATFORM:ACCOUNT:READ", "TENANT/42");
    }

    @Test
    @DisplayName("Rejects Duplicate Resources And Principals Outside The Input")
    void rejectsDuplicateResourcesAndPrincipalsOutsideTheInput() {
        ResourceLoader first = principals -> Map.of(
                PRINCIPAL.principalId(),
                List.of(new Operation("platform:account:read")));
        ResourceLoader duplicate = principals -> Map.of(
                PRINCIPAL.principalId(),
                List.of(new Operation("platform:account:read")));
        ResourceLoader extraPrincipal = principals -> Map.of(
                2L,
                List.of(new Operation("platform:account:read")));

        assertThatThrownBy(
                () -> new ResourceLoaders(List.of(first, duplicate), Runnable::run)
                        .load(List.of(PRINCIPAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Session 静态资源重复");
        assertThatThrownBy(
                () -> new ResourceLoaders(List.of(extraPrincipal), Runnable::run)
                        .load(List.of(PRINCIPAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("输入外的 Principal");
    }

    @Test
    @DisplayName("Rejects Null Results And Propagates Loader Failures")
    void rejectsNullResultsAndPropagatesLoaderFailures() {
        ResourceLoader nullResult = principals -> null;
        ResourceLoader nullResources = principals -> {
            Map<Long, Collection<? extends Resource>> result = new LinkedHashMap<>();
            result.put(PRINCIPAL.principalId(), null);
            return result;
        };
        ResourceLoader failed = principals -> {
            throw new IllegalArgumentException("loader failed");
        };

        assertThatThrownBy(
                () -> new ResourceLoaders(List.of(nullResult), Runnable::run)
                        .load(List.of(PRINCIPAL)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("不能返回 null");
        assertThatThrownBy(
                () -> new ResourceLoaders(List.of(nullResources), Runnable::run)
                        .load(List.of(PRINCIPAL)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Principal 静态资源不能为空");
        assertThatThrownBy(
                () -> new ResourceLoaders(List.of(failed), Runnable::run)
                        .load(List.of(PRINCIPAL)))
                .isInstanceOf(AuthException.class)
                .hasMessage("Session 静态资源装载失败")
                .hasRootCauseMessage("loader failed");
    }

    private static class TestPrincipal implements Principal {
        private final long id;

        private TestPrincipal(long id) {
            this.id = id;
        }

        @Override
        public long principalId() {
            return id;
        }
    }

    private static class TestResource implements Resource {
        private final String id;

        private final TreeCode type;

        private TestResource(String type, String id) {
            this.type = TreeCode.ofSegment(type);
            this.id = id;
        }

        @Override
        public @NotNull TreeCode type() {
            return type;
        }

        @Override
        public @NotNull String id() {
            return id;
        }
    }
}
