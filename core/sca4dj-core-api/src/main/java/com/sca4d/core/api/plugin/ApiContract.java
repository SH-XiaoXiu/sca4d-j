package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 由生成器实现类型安全客户端及实际对象分发器的构造。 */
public interface ApiContract<T> {
    /** 返回不可变契约。 */
    @NotNull ApiDefinition definition();
    /** 返回契约接口，类式声明使用生成的接口。 */
    @NotNull Class<T> type();
    /** 构造绑定当前调用入口的类型安全客户端。 */
    @NotNull T client(@NotNull ApiInvoker invoker, @Nullable TreeCode key);
    /** 将实际对象绑定为直接调用分发器。 */
    @NotNull ApiBinding bind(@NotNull T implementation, @Nullable TreeCode key);
}
