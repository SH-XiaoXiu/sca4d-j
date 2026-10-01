package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 分别报告新代次及旧代次状态，避免把生效后清理超时当成未更新。 */
public final class PluginUpdate {
    private final @NotNull PluginStatus current;

    private final @Nullable PluginStatus previous;

    public PluginUpdate(@NotNull PluginStatus current, @Nullable PluginStatus previous) {
        this.current = current;
        this.previous = previous;
    }

    /** @return 已经生效的新代次 */
    public @NotNull PluginStatus current() {
        return current;
    }

    /** @return 旧代次的清理状态；首次加载为空 */
    public @Nullable PluginStatus previous() {
        return previous;
    }
}
