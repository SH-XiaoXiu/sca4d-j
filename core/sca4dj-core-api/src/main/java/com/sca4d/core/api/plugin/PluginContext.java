package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 向当前插件代次提供已公开宿主服务和候选扩展登记操作。 */
public interface PluginContext {
    /** 返回当前插件描述。 */
    @NotNull PluginDescriptor descriptor();
    /** 取得已声明的宿主服务客户端；同步调用保留原线程，插件停止后拒绝继续使用。 */
    <T> @NotNull T service(@NotNull ApiContract<T> contract);
    /**
     * 在 onLoad 中登记当前代次扩展；发布后禁止登记，冲突或重复登记时失败。
     *
     * @param contract 宿主声明的扩展契约
     * @param implementation 当前插件实现；实现对象由运行时保存，不作为调用结果返回给宿主
     * @param key KEYED 扩展的稳定选择编码；SINGLE 扩展必须传入 null
     */
    <T> void register(@NotNull ApiContract<T> contract, @NotNull T implementation, @Nullable TreeCode key);
}
