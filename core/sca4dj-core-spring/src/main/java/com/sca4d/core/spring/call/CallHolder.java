package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** 保存当前线程的 `Call` 以及正在执行的嵌套 `CallExecution`。 */
public final class CallHolder {

    private static final ThreadLocal<@Nullable State> CURRENT = new ThreadLocal<>();

    private CallHolder() {
    }

    /**
     * 绑定当前线程唯一的调用。
     *
     * @param call 当前 HTTP、消息或任务调用
     */
    public static void set(Call call) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("当前调用已初始化，不能重复初始化");
        }
        CURRENT.set(new State(Objects.requireNonNull(call, "调用不能为空")));
    }

    /**
     * 取得当前调用。
     *
     * @return 当前调用
     * @throws IllegalStateException 当前线程不在受管理的调用中时抛出
     */
    public static Call current() {
        return currentState().call;
    }

    /**
     * 取得当前调用，允许在请求或任务之外调用。
     *
     * @return 当前调用；受管理调用之外为空
     */
    public static @Nullable Call currentOrNull() {
        State state = CURRENT.get();
        return state == null ? null : state.call;
    }

    /**
     * 把即将进入生命周期的方法执行压入当前线程。
     *
     * @param execution 与当前 `Call` 关联的方法执行
     */
    public static void push(CallExecution execution) {
        State state = currentState();
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        if (checked.call() != state.call) {
            throw new IllegalStateException("方法执行不属于当前调用");
        }
        state.executions.push(checked);
    }

    /**
     * 移除刚刚离开生命周期的方法执行。
     *
     * @param execution 必须是当前最内层的方法执行
     */
    public static void pop(CallExecution execution) {
        State state = currentState();
        if (state.executions.peek() != execution) {
            throw new IllegalStateException("方法执行栈离开顺序不一致");
        }
        state.executions.pop();
    }

    /**
     * 返回当前线程仍在生命周期内的方法执行。
     *
     * @return 从最内层到最外层排列的方法执行
     */
    public static List<CallExecution> executions() {
        return List.copyOf(currentState().executions);
    }

    /** 清理当前线程的调用和方法执行栈。 */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 取得当前线程状态。
     *
     * @return 已建立的调用状态
     */
    private static State currentState() {
        State state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException("当前调用尚未初始化");
        }
        return state;
    }

    /** 保存一个调用及其嵌套方法执行栈。 */
    private static final class State {

        private final Call call;

        private final Deque<CallExecution> executions = new ArrayDeque<>();

        /** @param call 当前线程唯一的调用 */
        private State(Call call) {
            this.call = call;
        }
    }
}
