package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.totp.credential.TotpCredential;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 不包含 Secret 和重放游标的当前 Account TOTP 状态。 */
public class TotpCredentialView {

    private final boolean enabled;

    private TotpCredentialView(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 创建已启用凭据的安全投影。
     *
     * @param credential 当前 TOTP 凭据
     * @return 已启用凭据的安全投影
     */
    public static @NotNull TotpCredentialView from(@NotNull TotpCredential credential) {
        Objects.requireNonNull(credential, "TOTP 凭据不能为空");
        return new TotpCredentialView(true);
    }

    /**
     * 返回当前 Account 是否启用 TOTP。
     *
     * @return 当前 Account 是否启用 TOTP
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
    public boolean isEnabled() {
        return enabled;
    }

}
