package com.sca4d.authentication.passkey;

import com.sca4d.authentication.AuthenticationProcess;
import org.jetbrains.annotations.NotNull;

/** 为一次认证创建并单次消费 WebAuthn assertion。 */
public interface PasskeyAuthenticationService {

    /** @return 绑定当前认证和浏览器的 assertion 及浏览器 request options */
    @NotNull
    PasskeyAssertionStart createAssertion(
            @NotNull String authenticationId, @NotNull String browserHash);

    /**
     * 单次消费 assertion 并提交 WebAuthn 响应。
     *
     * @param responseJson 浏览器返回的 PublicKeyCredential JSON
     * @return 当前认证正在等待的下一项输入
     */
    @NotNull
    AuthenticationProcess proof(
            @NotNull String authenticationId,
            @NotNull String assertionId,
            @NotNull String browserHash,
            @NotNull String responseJson);
}
