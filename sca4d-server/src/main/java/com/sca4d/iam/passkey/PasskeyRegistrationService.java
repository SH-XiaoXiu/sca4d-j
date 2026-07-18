package com.sca4d.iam.passkey;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;

/** 当前 Subject 为 Account 注册和删除 Passkey Credential。 */
public interface PasskeyRegistrationService {

    /**
     * 单次消费当前 Session Account 的 STEP_UP 并创建浏览器绑定 registration。
     *
     * @param displayName 账号安全页展示的 Passkey 名称
     * @return registration ID、过期时间和浏览器 WebAuthn creation options
     */
    @NotNull
    PasskeyRegistrationStart create(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String displayName);

    /**
     * 单次消费 registration、校验 WebAuthn attestation 并创建 ACTIVE Passkey Credential。
     *
     * @param responseJson 浏览器返回的 PublicKeyCredential JSON
     * @return 新 Credential 主键和初始版本
     */
    @NotNull
    PasskeyCredentialChange activate(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull String registrationId,
            @NotNull String responseJson,
            @NotNull String browserHash);

    /** 单次消费 STEP_UP 后按强 ETag 删除指定 Passkey，并结束目标 Account 全部 Session。 */
    void delete(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull String authenticationId,
            @NotNull String browserHash);
}
