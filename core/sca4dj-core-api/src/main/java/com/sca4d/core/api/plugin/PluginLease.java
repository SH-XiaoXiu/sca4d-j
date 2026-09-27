package com.sca4d.core.api.plugin;

/** 固定当前线程的一次同步插件调用范围；必须在同一线程关闭。 */
public interface PluginLease extends AutoCloseable {
    /** 释放当前嵌套租用；最外层关闭后允许旧代次清理。 */
    @Override void close();
}
