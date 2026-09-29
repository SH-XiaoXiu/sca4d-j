package com.sca4d.core.api.call;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 由集成层提供的默认调用值解析器，读取注解表达式中的参数、Bean 和执行结果。 */
@FunctionalInterface
public interface SPICallValueResolver extends CallValueResolver<Object> {

    /** 解析一项注解表达式。 */
    @Nullable
    Object resolve(@NotNull String expression, @NotNull CallExecution execution);

    /** 兼容按普通 CallValueResolver 调用的入口；空表达式会被解析器拒绝。 */
    @Override
    default @Nullable Object resolve(@NotNull CallExecution execution) {
        return resolve("", execution);
    }
}
