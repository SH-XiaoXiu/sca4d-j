package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.MissingAuthException;
import com.sca4d.common.auth.annotation.AuthAccount;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

/** 验证 Controller 的 Account 参数从 CurrentRequest 解析。 */
class AccountArgumentResolverTest {

    private final AccountArgumentResolver resolver = new AccountArgumentResolver();

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldResolveAccount() throws Exception {
        TestSession session = TestSession.of(7L);
        CurrentRequestHolder.set(CurrentRequest.authenticated(session));

        Object value = resolver.resolveArgument(parameter("account"), null, null, null);

        assertThat(value).isSameAs(session.getAccount());
    }

    @Test
    void shouldResolveAccountId() throws Exception {
        CurrentRequestHolder.set(CurrentRequest.authenticated(TestSession.of(8L)));

        Object value = resolver.resolveArgument(parameter("accountId"), null, null, null);

        assertThat(value).isEqualTo(8L);
    }

    @Test
    void shouldRejectMissingAccountWhenRequired() throws Exception {
        CurrentRequestHolder.set(new CurrentRequest());

        assertThatThrownBy(() -> resolver.resolveArgument(parameter("account"), null, null, null))
                .isInstanceOf(MissingAuthException.class);
    }

    @Test
    void shouldReturnNullWhenNullable() throws Exception {
        CurrentRequestHolder.set(new CurrentRequest());

        Object value = resolver.resolveArgument(parameter("nullableAccount"), null, null, null);

        assertThat(value).isNull();
    }

    @SuppressWarnings("unused")
    void endpoint(
            @AuthAccount Account account,
            @AuthAccount Long accountId,
            @AuthAccount(nullable = true) Account nullableAccount) {
    }

    /** @return endpoint 方法中指定名称对应的 Account 参数 */
    private MethodParameter parameter(String name) throws Exception {
        var method = getClass().getDeclaredMethod("endpoint", Account.class, Long.class, Account.class);
        String[] names = {"account", "accountId", "nullableAccount"};
        for (int index = 0; index < names.length; index++) {
            if (names[index].equals(name)) {
                return new MethodParameter(method, index);
            }
        }
        throw new IllegalArgumentException(name);
    }
}
