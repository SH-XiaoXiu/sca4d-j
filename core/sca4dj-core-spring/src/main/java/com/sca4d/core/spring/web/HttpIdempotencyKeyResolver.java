package com.sca4d.core.spring.web;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从当前 HTTP 请求的 `Idempotency-Key` Header 解析公共幂等键。 */
public final class HttpIdempotencyKeyResolver implements CallValueResolver<IdempotencyKey> {

    /** HTTP 调用方提交幂等键使用的固定 Header。 */
    public static final String HEADER = "Idempotency-Key";

    /** 创建 HTTP 幂等键解析器。 */
    public HttpIdempotencyKeyResolver() {
    }

    /**
     * 返回当前 HTTP 请求提交的幂等键。
     *
     * @param execution 当前方法执行；本解析器只读取当前 HTTP 请求
     * @return 去除首尾空白后的幂等键
     * @throws IllegalStateException 当前调用不是 HTTP 请求时抛出
     * @throws IllegalArgumentException Header 缺失、为空或超过 128 字符时抛出
     */
    @Override
    public @NotNull IdempotencyKey resolve(CallExecution execution) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new IllegalStateException("HttpIdempotencyKeyResolver 只能用于 HTTP 请求");
        }
        String value = attributes.getRequest().getHeader(HEADER);
        String checked = value == null ? "" : value.trim();
        if (checked.isEmpty() || checked.length() > 128 || checked.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(HEADER + " 格式错误");
        }
        return new IdempotencyKey(checked);
    }
}
