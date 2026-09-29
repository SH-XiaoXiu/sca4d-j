package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 返回插件代次的状态，不持有插件实现或异常对象。 */
public final class PluginStatus {
    /** 插件运行阶段。 */
    public enum State {
        /** 当前接受新调用。 */
        ACTIVE,
        /** 等待已进入的操作结束。 */
        DRAINING,
        /** 停止或资源释放失败。 */
        FAILED,
        /** 已停止并释放加载资源。 */
        STOPPED
    }

    private final @NotNull PluginDescriptor descriptor;

    private final long generation;

    private final @NotNull State state;

    private final int uses;

    private final @NotNull String digest;

    private final @Nullable String failure;

    public PluginStatus(
            @NotNull PluginDescriptor descriptor,
            long generation,
            @NotNull State state,
            int uses,
            @NotNull String digest,
            @Nullable String failure) {
        this.descriptor = descriptor;
        this.generation = generation;
        this.state = state;
        this.uses = uses;
        this.digest = digest;
        this.failure = failure;
    }

    /** 返回插件交付描述。 */
    public @NotNull PluginDescriptor descriptor() {
        return descriptor;
    }

    /** 返回当前实例的运行代次。 */
    public long generation() {
        return generation;
    }

    /** 返回实际运行阶段。 */
    public @NotNull State state() {
        return state;
    }

    /** 返回当前代次仍被操作持有的数量。 */
    public int uses() {
        return uses;
    }

    /** 返回交付物 SHA-256。 */
    public @NotNull String digest() {
        return digest;
    }

    /** 返回失败类型；没有失败时为空，不包含秘密和原始异常。 */
    public @Nullable String failure() {
        return failure;
    }
}
