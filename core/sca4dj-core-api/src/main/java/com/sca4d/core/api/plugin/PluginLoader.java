package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Set;

/** 加载库适配接口，公共契约不暴露任何库类型。 */
public interface PluginLoader {
    /** 检查 API 版本是否满足插件范围。 */
    boolean supportsVersion(@NotNull String version, @NotNull String range);

    /** 使用允许共享的宿主类型加载独立插件；实现不得借用未公开的宿主类。 */
    @NotNull
    LoadedPlugin load(
            @NotNull Path artifact,
            @NotNull PluginDescriptor descriptor,
            @NotNull Class<? extends PluginEntrypoint> entryBase,
            @NotNull Set<String> sharedClasses) throws Exception;
}
