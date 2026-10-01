package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 执行生成分发器已经绑定的单个方法。 */
@FunctionalInterface
public interface OperationInvoker {
    /** 按声明顺序调用实际目标，void 返回 null。 */
    @Nullable
    Object invoke(Object @NotNull [] arguments) throws Throwable;
}
