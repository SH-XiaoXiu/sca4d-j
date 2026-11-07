package com.sca4d.auth.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.error.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存外部 Provider 返回并已完成协议校验的 claims，按名称读取文本和布尔值；不包含 Token。 */
public final class FederatedClaims {

    private final @NotNull JsonNode node;

    /** 包装已经完成签名、受众和 nonce 校验的 claims 节点。 */
    public FederatedClaims(@NotNull JsonNode node) {
        this.node = Objects.requireNonNull(node, "claims 不能为空");
    }

    /**
     * 读取一个标量 claim 的文本。
     *
     * @param name claim 名称
     * @return 去除首尾空白后的文本；claim 缺失、为 null、为空白或不是标量时返回 null
     */
    public @Nullable String text(@NotNull String name) {
        JsonNode value = node.get(Objects.requireNonNull(name, "claim 名称不能为空"));
        if (value == null || !value.isValueNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().strip();
    }

    /**
     * 读取一个布尔 claim。
     *
     * @param name claim 名称
     * @return claim 是 JSON {@code true} 或文本 {@code true}（忽略大小写）时返回 true，缺失或其他取值返回 false
     */
    public boolean flag(@NotNull String name) {
        return "true".equalsIgnoreCase(text(name));
    }

    /**
     * 读取外部 Provider 内稳定的用户标识。
     *
     * @return {@code sub} claim 的文本
     * @throws BusinessException 缺少 {@code sub} 时抛出 {@link FederatedErrorCode#PROFILE_INVALID}
     */
    public @NotNull String subject() {
        String subject = text("sub");
        if (subject == null) {
            throw new BusinessException(FederatedErrorCode.PROFILE_INVALID);
        }
        return subject;
    }
}
