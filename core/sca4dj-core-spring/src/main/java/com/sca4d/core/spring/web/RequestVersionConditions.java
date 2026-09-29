package com.sca4d.core.spring.web;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallRequirement;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.web.annotation.IfMatch;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 把当前 HTTP 请求提交的 `If-Match` 版本绑定为必须由根资源 Repository 消费的调用要求。 */
public class RequestVersionConditions implements VersionConditions, CallLifecycleAdvisor.Declaration<IfMatch> {

    private final ThreadLocal<State> current = new ThreadLocal<>();

    /** 创建 HTTP 请求版本条件入口。 */
    public RequestVersionConditions() {
    }

    @Override
    public @NotNull Class<IfMatch> annotationType() {
        return IfMatch.class;
    }

    @Override
    public void attach(@NotNull CallExecution execution, @NotNull IfMatch annotation) {
        attach(execution);
    }

    /**
     * 绑定当前 HTTP 请求提交的版本前置条件。
     *
     * @param expectedVersion `If-Match` 强 ETag 中的版本
     * @throws IllegalStateException 当前线程已经绑定版本条件时抛出
     */
    public void bind(@NotNull Version expectedVersion) {
        if (current.get() != null) {
            throw new IllegalStateException("同一个 HTTP 请求只能绑定一个版本前置条件");
        }
        current.set(new State(Objects.requireNonNull(expectedVersion, "预期版本不能为空")));
    }

    /**
     * 把尚未附着的版本条件登记到处理 `@IfMatch` 的方法执行。
     *
     * @param execution 当前方法执行
     */
    public void attach(CallExecution execution) {
        State state = current.get();
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        if (state == null || state.attached || checked.declaration(IfMatch.class).isEmpty()) {
            return;
        }
        state.attached = true;
        checked.addRequirement(state);
    }

    /**
     * 返回根资源写入必须使用的版本条件，并标记当前 `If-Match` 已被消费。
     *
     * @param currentVersion Repository 已读取的资源当前版本
     * @return 已绑定 `If-Match` 时返回前置条件，否则返回普通乐观锁条件
     */
    @Override
    public @NotNull VersionCondition resolve(@NotNull Version currentVersion) {
        Version checkedCurrent = Objects.requireNonNull(currentVersion, "当前版本不能为空");
        State state = current.get();
        if (state == null) {
            return VersionCondition.optimistic(checkedCurrent);
        }
        if (state.consumed) {
            throw new IllegalStateException("同一个 HTTP 版本前置条件只能由一个根资源写入消费");
        }
        state.consumed = true;
        return VersionCondition.precondition(state.expectedVersion);
    }

    /** 清理当前线程保存的版本前置条件。 */
    public void clear() {
        current.remove();
    }

    /** 保存一个 `If-Match` 版本及其附着和消费状态。 */
    private static class State implements CallRequirement {

        private final @NotNull Version expectedVersion;

        private boolean attached;

        private boolean consumed;

        /** @param expectedVersion `If-Match` 声明的预期版本 */
        private State(@NotNull Version expectedVersion) {
            this.expectedVersion = expectedVersion;
        }

        /** 在业务写入成功前确认根资源 Repository 已经消费版本条件。 */
        @Override
        public void verifySatisfied() {
            if (!consumed) {
                throw new IllegalStateException("HTTP 方法声明了 @IfMatch，但根资源 Repository 没有消费版本前置条件");
            }
        }

        /**
         * `If-Match` 只约束写入，不在无关只读事务提交时检查。
         *
         * @return 固定返回 {@code false}
         */
        @Override
        public boolean appliesToReadOnlyTransaction() {
            return false;
        }
    }
}
