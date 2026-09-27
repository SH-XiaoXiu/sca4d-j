package com.sca4d.core.spring.web.clientstate;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 收集当前 HTTP 调用对客户端本地状态产生的变化。 */
public class ClientState {

    /** 客户端应重新读取的状态目标响应头。 */
    public static final String REFRESH_HEADER = "X-Client-State-Refresh";

    private static final ThreadLocal<@Nullable Change> CURRENT = new ThreadLocal<>();

    private ClientState() {
    }

    /**
     * 标记客户端需要重新读取指定状态。
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

    /** 当前调用收集到的客户端状态变化快照。 */
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
