package com.sca4d.auth.session.codec;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.PrincipalLoader;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证 `SessionIdentity` 与建立 `Session` 所需 Principal 的失败边界。 */
class PrincipalLoadersTest {

    @Test
    @DisplayName("Allows Identity Without Principals")
    void allowsIdentityWithoutPrincipals() {
        Subject subject = new Subject(11, SubjectType.HUMAN);
        SessionIdentity identity = new SessionIdentity(
                subject,
                new Account(22, subject.subjectId(), new RealmKey("test")));
        PrincipalLoaders principals = new PrincipalLoaders(
                List.of(new EmptyPrincipalLoader()));

        assertThat(principals.load(identity)).isEmpty();
    }

    /** 为测试返回空 Principal 集合。 */
    private static class EmptyPrincipalLoader implements PrincipalLoader {

        @Override
        public boolean supports(@NotNull SessionIdentity identity) {
            return true;
        }

        @Override
        public @NotNull List<? extends Principal> load(@NotNull SessionIdentity identity) {
            return List.of();
        }
    }
}
