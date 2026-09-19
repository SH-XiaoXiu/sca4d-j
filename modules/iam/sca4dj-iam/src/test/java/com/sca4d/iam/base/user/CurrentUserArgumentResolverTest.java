package com.sca4d.iam.base.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class CurrentUserArgumentResolverTest {

    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    @AfterEach
    void clearCall() {
        MDC.remove("principalId");
        CallHolder.clear();
    }

    @Test
    @DisplayName("Injects User And Principal ID While Binding Platform Principal")
    void injectsUserAndPrincipalIdWhileBindingPlatformPrincipal() throws Exception {
        Subject subject = new Subject(11, SubjectType.HUMAN);
        Account account = new Account(21, subject.subjectId(), PlatformIdentityService.PLATFORM_REALM);
        PlatformPrincipal principal = new PlatformPrincipal(31);
        Session session = new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(principal),
                List.of(),
                List.of(
                        new Authentication(
                                account.accountId(),
                                new AuthenticationMethodCode("password"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW)),
                Map.of(principal.principalId(), List.of()),
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();
        NativeWebRequest request = mock(NativeWebRequest.class);
        Call call = new Call("call-1", NOW);
        call.authenticate(session);
        CallHolder.set(call);

        Method method = TestController.class.getDeclaredMethod("current", User.class, Long.class);
        MethodParameter userParameter = new MethodParameter(method, 0);
        MethodParameter idParameter = new MethodParameter(method, 1);

        assertThat(resolver.supportsParameter(userParameter)).isTrue();
        assertThat(resolver.resolveArgument(userParameter, null, request, null))
                .isInstanceOfSatisfying(User.class, user -> {
                    assertThat(user.subjectId()).isEqualTo(subject.subjectId());
                    assertThat(user.accountId()).isEqualTo(account.accountId());
                    assertThat(user.principalId()).isEqualTo(principal.principalId());
                });
        assertThat(resolver.resolveArgument(idParameter, null, request, null)).isEqualTo(principal.principalId());
        assertThat(call.principal()).contains(principal);
    }

    /** 提供参数解析测试使用的 Controller 方法签名。 */
    private static final class TestController {

        void current(@CurrentUser User user, @CurrentUser Long principalId) {
            Objects.requireNonNull(user);
            Objects.requireNonNull(principalId);
        }
    }
}
