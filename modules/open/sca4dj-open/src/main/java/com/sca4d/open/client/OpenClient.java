package com.sca4d.open.client;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存第三方 Client 与 IAM APPLICATION Account 的稳定绑定。 */
public class OpenClient {

    private final @NotNull OpenClientStatus status;

    private final @NotNull String clientId;

    private final @NotNull String displayName;

    private final long accountId;

    /** 创建 Client 事实。 */
    public OpenClient(
            @NotNull String clientId,
            long accountId,
            @NotNull String displayName,
            @NotNull OpenClientStatus status) {
        this.clientId = TextUtils.requireText(clientId, "Client ID");
        if (accountId <= 0) {
            throw new IllegalArgumentException("Client Account 标识必须为正数");
        }
        this.accountId = accountId;
        this.displayName = TextUtils.requireText(displayName, "Client 名称");
        this.status = Objects.requireNonNull(status, "Client 状态不能为空");
    }

    /** @return 第三方请求使用的 Client ID */
    public @NotNull String clientId() {
        return clientId;
    }

    /** @return 对应 IAM Account 标识 */
    public long accountId() {
        return accountId;
    }

    /** @return 管理端显示名称 */
    public @NotNull String displayName() {
        return displayName;
    }

    /** @return 当前生命周期状态 */
    public @NotNull OpenClientStatus status() {
        return status;
    }

    /** @return Client 允许继续认证和调用时为 {@code true} */
    public boolean active() {
        return status == OpenClientStatus.ACTIVE;
    }

}
