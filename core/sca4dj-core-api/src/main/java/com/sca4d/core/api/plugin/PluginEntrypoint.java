package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.NotNull;

/** 供生成的 App 插件基类适配运行期入口，不表达具体业务。 */
public interface PluginEntrypoint {
    /** 初始化并登记候选扩展；发布前候选绑定不对业务可见，初始化期间不得执行无法撤销的业务动作。 */
    void onLoad(@NotNull PluginContext context) throws Exception;
    /** 释放插件自行创建的资源；初始化部分失败后也会调用，必须允许初始化尚未完成的状态。 */
    void onUnload() throws Exception;
}
