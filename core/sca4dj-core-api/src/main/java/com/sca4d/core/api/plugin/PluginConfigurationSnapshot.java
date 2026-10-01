package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 App 映射的插件描述与一个代次独立使用的公开配置，不决定资源格式或字段。 */
public final class PluginConfigurationSnapshot<C> {
    private final @NotNull C values;

    private final @NotNull PluginDescriptor descriptor;

    /** 保存描述与配置；App 必须提供不可变配置，避免活动代次受到后续重载影响。 */
    public PluginConfigurationSnapshot(@NotNull PluginDescriptor descriptor, @NotNull C values) {
        this.descriptor = Objects.requireNonNull(descriptor);
        this.values = Objects.requireNonNull(values);
    }

    /**
     * App 从插件元信息映射的运行描述。
     *
     * @return App 从插件元信息映射的运行描述
     */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull PluginDescriptor descriptor() {
        return descriptor;
    }

    /**
     * App 为当前代次提供的公开配置，不包含插件的私有实现对象。
     *
     * @return App 为当前代次提供的公开配置，不包含插件的私有实现对象
     */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull C values() {
        return values;
    }
}
