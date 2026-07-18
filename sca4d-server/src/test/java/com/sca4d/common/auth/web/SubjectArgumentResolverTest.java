package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.auth.MissingAuthException;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

/** 验证 Controller 的 Subject 参数从 CurrentRequest 解析。 */
class SubjectArgumentResolverTest {

    private final SubjectArgumentResolver resolver = new SubjectArgumentResolver();

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldResolveSubjectContext() throws Exception {
        Subject subject = new Subject(1L, "Alice");
        CurrentRequestHolder.set(CurrentRequest.authenticated(
                new TestSession("session-1", "token-1", subject)));

        Object value = resolver.resolveArgument(parameter("subject"), null, null, null);

        assertThat(value).isSameAs(subject);
    }

    @Test
    void shouldResolveSubjectId() throws Exception {
        Subject subject = new Subject(2L, "Bob");
        CurrentRequestHolder.set(CurrentRequest.authenticated(
                new TestSession("session-2", "token-2", subject)));

        Object value = resolver.resolveArgument(parameter("subjectId"), null, null, null);

        assertThat(value).isEqualTo(2L);
    }

    @Test
    void shouldRejectMissingSubjectWhenRequired() throws Exception {
        CurrentRequestHolder.set(new CurrentRequest());
        assertThatThrownBy(() -> resolver.resolveArgument(parameter("subject"), null, null, null))
                .isInstanceOf(MissingAuthException.class);
    }

    @Test
    void shouldReturnNullWhenNullable() throws Exception {
        CurrentRequestHolder.set(new CurrentRequest());
        Object value = resolver.resolveArgument(parameter("nullableSubject"), null, null, null);

        assertThat(value).isNull();
    }

    @SuppressWarnings("unused")
    void endpoint(
            @AuthSubject Subject subject,
            @AuthSubject Long subjectId,
            @AuthSubject(nullable = true) Subject nullableSubject) {
    }

    /** @return endpoint 方法中指定名称对应的 Subject 参数 */
    private MethodParameter parameter(String name) throws Exception {
        var method = getClass().getDeclaredMethod("endpoint", Subject.class, Long.class, Subject.class);
        String[] names = {"subject", "subjectId", "nullableSubject"};
        for (int index = 0; index < names.length; index++) {
            if (names[index].equals(name)) {
                return new MethodParameter(method, index);
            }
        }
        throw new IllegalArgumentException(name);
    }
}
