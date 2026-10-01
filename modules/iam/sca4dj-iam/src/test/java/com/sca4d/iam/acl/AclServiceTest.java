package com.sca4d.iam.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.iam.DomainScope;
import com.sca4d.iam.identity.IdentityService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class AclServiceTest {
    private static final TreeCode ORDER_TYPE = TreeCode.ofSlash("SHOP/ORDER");

    @Test
    @DisplayName("重复授权保持幂等，并能撤销资源权限")
    void grantsIdempotentlyAndRevokesPermission() {
        FakeRepository repository = new FakeRepository(new DomainScope("shop"), Set.of(ORDER_TYPE));
        IdentityService identities = mock(IdentityService.class);
        long principalId = 10L;
        when(identities.isActivePrincipal(principalId)).thenReturn(true);
        AclService acl = service(repository, identities);

        acl.grant(repository.scope(), principalId, ORDER_TYPE, "42", Action.READ);
        acl.grant(repository.scope(), principalId, ORDER_TYPE, "42", Action.READ);
        assertThat(acl.allows(repository.scope(), principalId, ORDER_TYPE, "42", Action.READ)).isTrue();
        assertThat(acl.findAllowedResourceIds(repository.scope(), principalId, ORDER_TYPE, Action.READ))
                .containsExactly("42");

        acl.revoke(repository.scope(), principalId, ORDER_TYPE, "42", Action.READ);
        assertThat(acl.allows(repository.scope(), principalId, ORDER_TYPE, "42", Action.READ)).isFalse();
    }

    @Test
    @DisplayName("Principal 停用后清除其 ACL 条目")
    void clearsEntriesAfterPrincipalBecomesInactive() {
        FakeRepository repository = new FakeRepository(new DomainScope("shop"), Set.of(ORDER_TYPE));
        IdentityService identities = mock(IdentityService.class);
        long principalId = 10L;
        when(identities.isActivePrincipal(principalId)).thenReturn(true);
        AclService acl = service(repository, identities);
        acl.grant(repository.scope(), principalId, ORDER_TYPE, "42", Action.READ);
        when(identities.isActivePrincipal(principalId)).thenReturn(false);

        acl.revokeByPrincipal(repository.scope(), principalId);

        assertThat(repository.entries).isEmpty();
    }

    @Test
    @DisplayName("拒绝 Operation 资源类型和跨 Domain 重复资源类型")
    void rejectsOperationAndCrossDomainDuplicateResourceTypes() {
        assertThatThrownBy(
                () -> registerTypes(
                        new FakeRepository(new DomainScope("a"), Set.of(Operation.RESOURCE_TYPE))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                () -> registerTypes(
                        new FakeRepository(new DomainScope("a"), Set.of(ORDER_TYPE)),
                        new FakeRepository(new DomainScope("b"), Set.of(ORDER_TYPE))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AclService service(AclRepository repository, IdentityService identities) {
        AclRepositoryRegistry registry = new AclRepositoryRegistry(List.of(repository));
        return new AclService(registry, new AclResourceTypes(registry), identities);
    }

    private static void registerTypes(AclRepository... repositories) {
        new AclResourceTypes(new AclRepositoryRegistry(List.of(repositories)));
    }

    private static class FakeRepository implements AclRepository {
        private final DomainScope scope;

        private final Set<TreeCode> types;

        private final List<AclEntry> entries = new ArrayList<>();

        private FakeRepository(DomainScope scope, Set<TreeCode> types) {
            this.scope = scope;
            this.types = types;
        }

        @Override
        @CheckReturnValue
        public @NotNull DomainScope scope() {
            return scope;
        }

        @Override
        @CheckReturnValue
        public @NotNull @Unmodifiable Set<@NotNull TreeCode> resourceTypes() {
            return types;
        }

        @Override
        public void grant(
                @NotNull Long p,
                @NotNull TreeCode t,
                @NotNull String id,
                @NotNull Action a) {
            if (!allows(p, t, id, a))
                entries.add(new AclEntry(p, t, id, a));
        }

        @Override
        public void revoke(
                @NotNull Long p,
                @NotNull TreeCode t,
                @NotNull String id,
                @NotNull Action a) {
            entries.removeIf(
                    e -> e.principalId().equals(p)
                            && e.resourceType().equals(t)
                            && e.resourceId().equals(id)
                            && e.action().equals(a));
        }

        @Override
        @CheckReturnValue
        public boolean allows(
                @NotNull Long p,
                @NotNull TreeCode t,
                @NotNull String id,
                @NotNull Action a) {
            return entries.stream()
                    .anyMatch(
                            e -> e.principalId().equals(p)
                                    && e.resourceType().equals(t)
                                    && e.resourceId().equals(id)
                                    && e.action().equals(a));
        }

        @Override
        @CheckReturnValue
        public @NotNull @Unmodifiable List<@NotNull String> findAllowedResourceIds(
                @NotNull Long p,
                @NotNull TreeCode t,
                @NotNull Action a) {
            return entries.stream()
                    .filter(
                            e -> e.principalId().equals(p)
                                    && e.resourceType().equals(t)
                                    && e.action().equals(a))
                    .map(AclEntry::resourceId)
                    .toList();
        }

        @Override
        @CheckReturnValue
        public @NotNull @Unmodifiable List<@NotNull AclEntry> findEntries(
                @NotNull Long p,
                @NotNull TreeCode t) {
            return List.copyOf(entries);
        }

        @Override
        public void revokeByPrincipal(@NotNull Long p) {
            entries.removeIf(e -> e.principalId().equals(p));
        }

        @Override
        public void revokeByResource(@NotNull TreeCode t, @NotNull String id) {
            entries.removeIf(e -> e.resourceType().equals(t) && e.resourceId().equals(id));
        }
    }
}
