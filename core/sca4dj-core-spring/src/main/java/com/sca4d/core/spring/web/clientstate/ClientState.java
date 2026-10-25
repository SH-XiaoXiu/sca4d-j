package com.sca4d.core.spring.web.clientstate;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 收集当前 HTTP 调用对客户端本地状态产生的变化。 */
public class ClientState {

    private static final ThreadLocal<@Nullable Change> CURRENT = new ThreadLocal<>();

    private ClientState() {
    }

    /**
     * 把状态目标记入当前线程的变化集合，同一目标重复登记只保留一次。
     * 变化保存在 ThreadLocal 中，由 ClientStateFilter 在响应结束后清理；异步线程不会继承这里的记录。
     *
     * @param target 需要客户端重新读取的状态目标
     */
    public static void refresh(ClientStateTarget target) {
        current().targets.add(target);
    }

    /**
     * 返回当前请求收集的状态变化。
     *
     * @return 当前请求收集的状态变化；没有变化时为空
     */
    public static @Nullable Change currentOrNull() {
        return CURRENT.get();
    }

    /** 清理当前线程收集的客户端状态变化。 */
    public static void clear() {
        CURRENT.remove();
    }

    private static Change current() {
        Change change = CURRENT.get();
        if (change == null) {
            change = new Change();
            CURRENT.set(change);
        }
        return change;
    }

    /** 保存当前线程登记的客户端状态目标，按登记顺序去重。 */
    public static class Change {

        private final Set<ClientStateTarget> targets = new LinkedHashSet<>();

        private Change() {
        }

        /**
         * 返回需要客户端重新读取的状态目标。
         *
         * @return 去重、保持登记顺序的不可变刷新目标
         */
        public @Unmodifiable Set<ClientStateTarget> targets() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(targets));
        }

    }
}
