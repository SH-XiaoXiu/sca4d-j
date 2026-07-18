package com.sca4d.common.web;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 前端本地状态刷新信号。
 *
 * <p>业务代码修改会话、身份、主体、权限或菜单后，在当前请求内写入刷新信号，Web 过滤器再把信号写入响应头。
 */
public final class ClientState {
    /** 前端本地状态已过期。 */
    public static final String STALE_HEADER = "X-Client-State-Stale";
    /** 前端需要重新拉取的状态目标。 */
    public static final String REFRESH_HEADER = "X-Client-State-Refresh";
    /** 前端可用于比较的状态版本。 */
    public static final String VERSION_HEADER = "X-Client-State-Version";
    private static final ThreadLocal<Change> CURRENT = new ThreadLocal<>();

    /** 禁止创建请求级状态信号工具实例。 */
    private ClientState() {
    }

    /**
     * 标记前端本地状态已过期。
     */
    public static void stale() {
        current().setStale(true);
    }

    /**
     * 标记前端需要刷新指定状态目标。
     *
     * @param target 业务模块声明的固定或资源级状态目标
     */
    public static void refresh(@NotNull ClientStateTarget target) {
        current().addRefreshTarget(target);
    }

    /**
     * 设置前端状态版本。
     *
     * @param version 状态版本
     */
    public static void version(@Nullable String version) {
        if (StringUtils.hasText(version)) {
            current().setVersion(version.trim());
        }
    }

    /**
     * 读取当前请求的状态变化。
     *
     * @return 状态变化；没有变化时返回 null
     */
    public static @Nullable Change currentOrNull() {
        return CURRENT.get();
    }

    /**
     * 清理当前请求状态变化。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 读取或创建当前线程在本次请求中的状态变化容器。
     *
     * @return 绑定到当前线程的可变变化容器
     */
    private static @NotNull Change current() {
        Change change = CURRENT.get();
        if (change == null) {
            change = new Change();
            CURRENT.set(change);
        }
        return change;
    }

    /**
     * 当前请求内的前端状态变化。
     */
    public static final class Change {

        private boolean stale;
        private final Set<ClientStateTarget> refreshTargets = new LinkedHashSet<>();
        private String version;

        /** 创建仅绑定当前请求线程的空状态变化。 */
        private Change() {
        }

        /**
         * 判断前端是否应放弃当前本地状态快照。
         *
         * @return 任一业务操作标记客户端状态过期时返回 {@code true}
         */
        @Contract(pure = true)
        public boolean isStale() {
            return stale;
        }

        /** @param stale 当前请求是否使前端本地状态过期 */
        private void setStale(boolean stale) {
            this.stale = stale;
        }

        /**
         * 返回需要重新拉取的状态目标快照。
         *
         * @return 去重且保持首次登记顺序的不可变集合
         */
        public @NotNull Set<ClientStateTarget> getRefreshTargets() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(refreshTargets));
        }

        /** @param refreshTarget 当前请求新增的固定或资源级状态目标 */
        private void addRefreshTarget(@NotNull ClientStateTarget refreshTarget) {
            this.refreshTargets.add(refreshTarget);
        }

        /**
         * 返回前端可用于比较的状态版本。
         *
         * @return 调用方登记的版本；未登记时返回 {@code null}
         */
        public @Nullable String getVersion() {
            return version;
        }

        /** @param version 当前请求产生的可选状态版本 */
        private void setVersion(@Nullable String version) {
            this.version = version;
        }

        /**
         * 判断当前请求是否登记了任一需要输出的状态变化。
         *
         * @return 过期标记、刷新目标或版本任一存在时返回 {@code true}
         */
        public boolean hasChange() {
            return stale || !refreshTargets.isEmpty() || StringUtils.hasText(version);
        }
    }
}
