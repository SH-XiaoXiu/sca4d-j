package com.sca4d.auth.federation;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 引用 Redis 中一次性外部授权完成结果的认证材料。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class FederatedProof {

    private final @NotNull String authorizationId;

    private final @NotNull String browserBinding;

    /**
     * 创建绑定到当前浏览器的一次性认证材料。
     *
     * @param authorizationId 回调生成的短期一次性授权完成标识
     * @param browserBinding 当前浏览器绑定摘要
     */
    public FederatedProof(@NotNull String authorizationId, @NotNull String browserBinding) {
        String value = Objects.requireNonNull(authorizationId, "外部授权标识不能为空").strip();
        if (!value.matches("[A-Za-z0-9_-]{20,128}"))
            throw new IllegalArgumentException("外部授权标识格式无效");
        this.authorizationId = value;
        this.browserBinding = Objects.requireNonNull(browserBinding, "浏览器绑定不能为空");
    }

    /** @return 一次性授权完成标识 */
    public @NotNull String authorizationId() {
        return authorizationId;
    }

    /** @return 当前浏览器或匿名流程绑定摘要 */
    public @NotNull String browserBinding() {
        return browserBinding;
    }
}
