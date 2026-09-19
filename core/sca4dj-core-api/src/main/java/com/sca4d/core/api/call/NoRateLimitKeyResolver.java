package com.sca4d.core.api.call;

/** 表示 `@RateLimited` 没有声明额外的自定义限流键解析器。 */
public final class NoRateLimitKeyResolver implements CallValueResolver<String> {

    /** 始终拒绝直接调用只用于注解默认值的标记解析器。 */
    @Override
    public String resolve(CallExecution execution) {
        throw new UnsupportedOperationException("空限流键解析器不能执行");
    }
}
