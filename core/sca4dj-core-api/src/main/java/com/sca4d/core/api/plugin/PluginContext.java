package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 向当前插件代次提供已公开宿主服务和候选扩展登记操作。 */
public interface PluginContext {
    /** 返回当前插件描述。 */
    @NotNull
    PluginDescriptor descriptor();

    /**
     * 返回当前代次的 App 公开配置；生成上下文使用准确类型调用此入口。
     * 类型不一致或代次停止后访问时失败，旧作用域在排空期间继续使用旧配置。
     *
     * @param type App 配置提供者声明的同一个共享类型
     * @return 当前代次独立使用的不可变配置
     */
    <C> @NotNull C configuration(@NotNull Class<C> type);

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
