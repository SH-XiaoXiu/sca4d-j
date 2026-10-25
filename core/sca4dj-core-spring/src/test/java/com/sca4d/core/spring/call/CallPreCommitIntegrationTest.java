package com.sca4d.core.spring.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.ResourceScope;
import com.sca4d.core.api.permission.ResourceScopeRequirement;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.spring.permission.CurrentResourceScope;
import com.sca4d.core.spring.web.RequestVersionConditions;
import com.sca4d.core.spring.web.annotation.IfMatch;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证 Spring 事务和嵌套方法执行会共同落实调用成功要求。
 */
class CallPreCommitIntegrationTest {

    private final RequestVersionConditions versionConditions = new RequestVersionConditions();

    @AfterEach
    void clearCall() {
        CallHolder.clear();
        versionConditions.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("Rejects Transaction Commit Before If Match Is Consumed")
    void rejectsTransactionCommitBeforeIfMatchIsConsumed() throws Exception {
        Call call = Call
                .system("transaction-requirement", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        CallHolder.set(call);
        CallExecution execution = execution(call, Fixture.class.getDeclaredMethod("versioned"));
        CallHolder.push(execution);
        execution.beginInvocation();
        versionConditions.bind(new Version(3));
        versionConditions.attach(execution);
        TransactionExecution transaction = mock(TransactionExecution.class);
        when(transaction.isReadOnly()).thenReturn(false);
        CallPreCommitTransactionListener listener = new CallPreCommitTransactionListener();

        assertThatThrownBy(() -> listener.beforeCommit(transaction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository 没有消费版本前置条件");

        versionConditions.resolve(new Version(4));

        assertThatCode(() -> listener.beforeCommit(transaction)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Consumes Matching Resource Scope For Every Active Execution")
    void consumesMatchingResourceScopeForEveryActiveExecution() throws Exception {
        Call call = Call.system("nested-scope", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        Permission permission = new Permission(new DeviceCollection(), Action.READ);
        DeviceScope scope = new DeviceScope();
        call.recordPermissionDecision(permission, PermissionDecision.allow(scope));
        CallExecution outer = execution(call, Fixture.class.getDeclaredMethod("plain"));
        CallExecution inner = execution(call, Fixture.class.getDeclaredMethod("nested"));
        ResourceScopeRequirement.register(outer, permission);
        ResourceScopeRequirement.register(inner, permission);
        CallHolder.set(call);
        CallHolder.push(outer);
        CallHolder.push(inner);

        assertThat(new CurrentResourceScope().scope(permission, DeviceScope.class)).isSameAs(scope);
        assertThatCode(outer::verifyPreCommit).doesNotThrowAnyException();
        assertThatCode(inner::verifyPreCommit).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("提交后回调在方法结果记录前运行，提交失败时不运行")
    void runsAfterCommitBeforeMethodResultExists() throws Exception {
        Call call = Call.system("after-commit", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        CallExecution execution = execution(call, Fixture.class.getDeclaredMethod("plain"));
        AtomicInteger committed = new AtomicInteger();
        execution.addAfterCommit(new CallLifecycle.AfterCommit() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("TEST/AFTER_COMMIT");
            }

            @Override
            public void afterCommit(@NotNull CallExecution current) {
                assertThat(current.outcome()).isEmpty();
                committed.incrementAndGet();
            }
        });
        CallHolder.set(call);
        CallHolder.push(execution);
        execution.beginInvocation();
        // 先标记实际事务已开始并初始化同步，再调用 track 登记 execution，模拟方法执行发生在 Spring 事务内的顺序。
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        CallPreCommitTransactionListener.track(execution);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCommit();

        assertThat(committed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("外层事务晚于方法结束时仍检查条件并执行提交后回调")
    void tracksExecutionUntilOuterTransactionCommits() throws Exception {
        Call call = Call.system("outer-transaction", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        CallExecution execution = execution(call, Fixture.class.getDeclaredMethod("plain"));
        AtomicInteger checked = new AtomicInteger();
        AtomicInteger committed = new AtomicInteger();
        execution.addPreCommit(checked::incrementAndGet);
        execution.addAfterCommit(new CallLifecycle.AfterCommit() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("TEST/OUTER_COMMIT");
            }

            @Override
            public void afterCommit(@NotNull CallExecution current) {
                committed.incrementAndGet();
            }
        });
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        CallHolder.set(call);
        CallHolder.push(execution);
        CallPreCommitTransactionListener.track(execution);
        execution.beginInvocation();
        execution.endInvocation();
        execution.recordOutcome(CallOutcome.success("done"));
        CallHolder.pop(execution);
        CallHolder.clear();

        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.beforeCommit(false);
        synchronization.afterCommit();

        assertThat(checked.get()).isEqualTo(1);
        assertThat(committed.get()).isEqualTo(1);
    }

    private static CallExecution execution(Call call, Method method) {
        return new CallExecution(call, Fixture.class, method, new Object[0], method.getGenericReturnType());
    }

    /**
     * 提供普通、嵌套和版本保护方法声明。
     */
    private static class Fixture {

        private void plain() {
        }

        private void nested() {
        }

        @IfMatch
        private void versioned() {
        }
    }

    /**
     * 表示测试中的设备集合资源。
     */
    private static class DeviceCollection implements Resource {

        @Override
        public @NotNull TreeCode type() {
            return TreeCode.ofSlash("TEST/DEVICE");
        }

        @Override
        public @NotNull String id() {
            return "collection";
        }
    }

    /**
     * 表示测试中的设备数据范围。
     */
    private static class DeviceScope implements ResourceScope {

        @Override
        public TreeCode resourceType() {
            return TreeCode.ofSlash("TEST/DEVICE");
        }
    }
}
