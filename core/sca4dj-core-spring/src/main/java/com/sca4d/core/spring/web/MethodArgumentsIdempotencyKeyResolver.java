package com.sca4d.core.spring.web;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用规范化方法参数摘要生成不暴露原始参数的幂等键。 */
public final class MethodArgumentsIdempotencyKeyResolver implements CallValueResolver<IdempotencyKey> {

    private final IdempotencyFingerprints fingerprints;

    /**
     * 创建方法参数幂等键解析器。
     *
     * @param fingerprints 公共参数规范化入口
     */
    public MethodArgumentsIdempotencyKeyResolver(IdempotencyFingerprints fingerprints) {
        this.fingerprints = Objects.requireNonNull(fingerprints, "幂等参数规范化入口不能为空");
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull IdempotencyKey resolve(CallExecution execution) {
        return new IdempotencyKey(fingerprints.argumentsDigest(execution.arguments()));
    }
}
