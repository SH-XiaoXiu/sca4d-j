package com.sca4d.core.spring.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 验证自主执行选择稳定领域身份、保留已有调用并清理失败后的线程状态。 */
class SystemExecutionCallTest {

    @BeforeAll
    static void initializeIdentityFixture() {
        SystemPrincipals.publish(
                Map.of(
                        SystemPrincipals.MAIN,
                        new SystemPrincipal(1, SystemPrincipals.MAIN),
                        TreeCode.ofSegment("IOT"),
                        new SystemPrincipal(2, TreeCode.ofSegment("IOT"))));
    }

    @AfterEach
    void clearCall() {
        CallHolder.clear();
    }

    @Test
    void selectsSystemDomainAndClearsTheCompletedCall() {
        Call call = task().current();
        assertThat(call.subject()).contains(Subject.SYSTEM);
        assertThat(call.principal().orElseThrow().principalId()).isEqualTo(2);
        assertThat(CallHolder.currentOrNull()).isNull();
        assertThatThrownBy(() -> task().fail()).isInstanceOf(IllegalStateException.class);
        assertThat(CallHolder.currentOrNull()).isNull();
    }

    @Test
    void preservesAnExistingCallerWithoutReplacingItsIdentity() {
        Call original = new Call("existing", Instant.EPOCH);
        CallHolder.set(original);
        assertThat(task().current()).isSameAs(original);
        assertThat(CallHolder.current()).isSameAs(original);
        assertThat(original.principal()).isEmpty();
    }

    /** 创建实际 Spring 切面代理，不依赖容器或数据库。 */
    private static AutonomousTask task() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new AutonomousTask());
        CallInvoker lifecycle = new CallInvoker(List.of(new IdentityCallInterceptor()));
        CallLifecycleAdvisor.Declaration<SystemExecution> declaration = new CallLifecycleAdvisor.Declaration<>() {
            @Override
            public @NonNull Class<SystemExecution> annotationType() {
                return SystemExecution.class;
            }

            @Override
            public @NonNull Optional<Call> createCall(String id, Instant at, SystemExecution annotation) {
                return Optional.of(Call.system(id, at, SystemPrincipals.require(TreeCode.of(annotation.value()))));
            }
        };
        factory.addAdvisor(
                new CallLifecycleAdvisor(lifecycle, List.of(declaration), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        return factory.getProxy();
    }

    /** 声明自主入口的测试业务对象。 */
    public static class AutonomousTask {
        /** 返回入口实际使用的 Call，供身份行为断言。 */
        @SystemExecution("IOT")
        public Call current() {
            return CallHolder.current();
        }

        /** 模拟自主入口失败，供线程状态清理断言。 */
        @SystemExecution("IOT")
        public void fail() {
            throw new IllegalStateException("failed");
        }
    }
}
