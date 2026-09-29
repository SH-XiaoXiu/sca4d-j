package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.web.annotation.IfMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

/** 验证 HTTP 版本条件只约束实际声明 `@IfMatch` 的方法执行。 */
class RequestVersionConditionsTest {

    private final RequestVersionConditions conditions = new RequestVersionConditions();

    @AfterEach
    void clearConditions() {
        conditions.clear();
    }

    @Test
    @DisplayName("Attaches If Match Requirement Only To Annotated Execution")
    void attachesIfMatchRequirementOnlyToAnnotatedExecution() throws Exception {
        conditions.bind(new Version(3));
        Call call = Call.system("if-match", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        CallExecution plain = execution(call, Fixture.class.getDeclaredMethod("plain"));
        CallExecution versioned = execution(call, Fixture.class.getDeclaredMethod("versioned"));

        conditions.attach(plain);
        conditions.attach(versioned);

        assertThatCode(plain::verifyPreCommit).doesNotThrowAnyException();
        assertThatThrownBy(versioned::verifyPreCommit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository 没有消费版本前置条件");
        assertThat(conditions.resolve(new Version(4)).expected()).isEqualTo(new Version(3));
        assertThatCode(versioned::verifyPreCommit).doesNotThrowAnyException();
    }

    private static CallExecution execution(Call call, Method method) {
        return new CallExecution(call, Fixture.class, method, new Object[0], method.getGenericReturnType());
    }

    /** 提供普通方法和版本保护方法的反射声明。 */
    private static class Fixture {

        private void plain() {
        }

        @IfMatch
        private void versioned() {
        }
    }
}
