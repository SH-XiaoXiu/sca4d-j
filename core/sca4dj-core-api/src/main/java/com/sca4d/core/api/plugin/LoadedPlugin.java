package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.NotNull;

/** 保存已加载入口和加载器；关闭只释放加载资源，不代替业务停止。 */
public interface LoadedPlugin extends AutoCloseable {
    /** 返回已经按 App 基类检查的入口。 */
    @NotNull PluginEntrypoint entrypoint();
    /** 返回当前代次的加载器。 */
    @NotNull ClassLoader classLoader();
    /** 关闭加载器和交付物资源。 */
    @Override void close() throws Exception;
}
