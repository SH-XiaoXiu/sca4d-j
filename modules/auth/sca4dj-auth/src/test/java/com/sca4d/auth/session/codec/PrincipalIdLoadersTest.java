package com.sca4d.auth.session.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdLoader;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

class PrincipalIdLoadersTest {

    @Test
    @DisplayName("Loads Distinct Principal Types And Omits Empty Sets")
    void loadsDistinctPrincipalTypesAndOmitsEmptySets() {
        PrincipalIdLoaders loaders = new PrincipalIdLoaders(
                List.of(
                        new FixedLoader<>(TestPrincipal.class, List.of(31L, 32L)),
                        new FixedLoader<>(OtherPrincipal.class, List.of(41L)),
                        new FixedLoader<>(EmptyPrincipal.class, List.of())));

        var result = loaders.load(identity());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).principalType()).isEqualTo(TestPrincipal.class);
        assertThat(result.get(0).ids().toArray()).containsExactly(31L, 32L);
        assertThat(result.get(1).principalType()).isEqualTo(OtherPrincipal.class);
    }

    @Test
    @DisplayName("Rejects Duplicate Principal Type And IDs")
    void rejectsDuplicatePrincipalTypeAndIds() {
        assertThatThrownBy(
                () -> new PrincipalIdLoaders(
                        List.of(
                                new FixedLoader<>(TestPrincipal.class, List.of(31L)),
                                new FixedLoader<>(TestPrincipal.class, List.of(32L))))
                        .load(identity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("多个 PrincipalIdLoader");
        assertThatThrownBy(
                () -> new PrincipalIdLoaders(
                        List.of(
                                new FixedLoader<>(TestPrincipal.class, List.of(31L, 31L))))
                        .load(identity()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复值");
        assertThatThrownBy(
                () -> new PrincipalIdLoaders(
                        List.of(
                                new FixedLoader<>(TestPrincipal.class, List.of()),
                                new FixedLoader<>(TestPrincipal.class, List.of(32L))))
                        .load(identity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("多个 PrincipalIdLoader");
    }

    private static SessionIdentity identity() {
        Subject subject = new Subject(11, SubjectType.HUMAN);
        return new SessionIdentity(subject, new Account(22, subject.subjectId(), new RealmKey("test")));
    }

    /** 为测试返回固定 Principal 类型和标识。 */
    @SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通测试类型。
    private static final class FixedLoader<P extends Principal> implements PrincipalIdLoader<P> {
        private final Class<P> type;

        private final Collection<Long> ids;

        private FixedLoader(Class<P> type, Collection<Long> ids) {
            this.type = type;
            this.ids = ids;
        }

        @Override
        public boolean supports(@NotNull SessionIdentity identity) {
            return true;
        }

        @Override
        public @NotNull Class<P> principalType() {
            return type;
        }

        @Override
        public @NotNull Collection<@NotNull Long> load(@NotNull SessionIdentity identity) {
            return ids;
        }
    }

    /** 测试使用的第一种 Principal。 */
    private static final class TestPrincipal implements Principal {
        @Override
        public long principalId() {
            return 31;
        }
    }

    /** 测试使用的第二种 Principal。 */
    private static final class OtherPrincipal implements Principal {
        @Override
        public long principalId() {
            return 41;
        }
    }

    /** 验证空标识集合不会进入 Session 的 Principal。 */
    private static final class EmptyPrincipal implements Principal {
        @Override
        public long principalId() {
            return 51;
        }
    }
}
