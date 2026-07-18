package com.sca4d.authentication.recovery;

import com.sca4d.iam.identifier.AccountIdentifierType;
import org.jetbrains.annotations.NotNull;

/** RecoveryCodeAuthMethod 在一个 Account 作用域内定位账号并校验恢复码所需的 Proof。 */
public final class RecoveryCodeAuthenticationProof {

    private final AccountIdentifierType identifierType;
    private final String identifier;
    private final String recoveryCode;

    /**
     * 创建只存在于当前请求的恢复码 Proof。
     *
     * @param identifierType USERNAME 或 EMAIL
     * @param identifier 当前认证账号范围内的登录标识
     * @param recoveryCode 用户保存的一次性恢复码
     */
    public RecoveryCodeAuthenticationProof(
            @NotNull AccountIdentifierType identifierType,
            @NotNull String identifier,
            @NotNull String recoveryCode) {
        this.identifierType = identifierType;
        this.identifier = identifier;
        this.recoveryCode = recoveryCode;
    }

    /** @return AccountIdentifier 类型 */
    public @NotNull AccountIdentifierType identifierType() {
        return identifierType;
    }

    /** @return 当前认证账号范围内的登录标识 */
    public @NotNull String identifier() {
        return identifier;
    }

    /** @return 只允许 RecoveryCodeService 在当前调用中校验的恢复码 */
    public @NotNull String recoveryCode() {
        return recoveryCode;
    }
}
