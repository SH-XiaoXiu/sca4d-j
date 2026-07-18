package com.sca4d.iam.error;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.UnauthenticatedException;
import org.jetbrains.annotations.NotNull;

/** 客户端含义仍为共用认证失败、但内部审计已经安全识别 Credential 的异常。 */
public class KnownCredentialRejectedException extends UnauthenticatedException {

    private final Long credentialId;
    private final Long subjectId;

    /**
     * 创建不包含登录账号和认证材料的已知凭证拒绝数据。
     *
     * @param credentialId 已匹配的 Credential 主键
     * @param subjectId Credential 所属 Subject 主键
     */
    public KnownCredentialRejectedException(@NotNull Long credentialId, @NotNull Long subjectId) {
        this(credentialId, subjectId, AuthenticationErrorCode.BAD_CREDENTIAL);
    }

    /**
     * 创建保留具体认证错误、但不包含认证材料的已知凭证拒绝数据。
     *
     * @param credentialId 已匹配的 Credential 主键
     * @param subjectId Credential 所属 Subject 主键
     * @param errorCode 对客户端公开且不泄漏凭证状态的认证错误
     */
    public KnownCredentialRejectedException(
            @NotNull Long credentialId,
            @NotNull Long subjectId,
            @NotNull AuthenticationErrorCode errorCode) {
        super(errorCode);
        this.credentialId = credentialId;
        this.subjectId = subjectId;
    }

    /** @return 已匹配但验证失败的 Credential 主键 */
    public @NotNull Long getCredentialId() {
        return credentialId;
    }

    /** @return 已匹配 Credential 所属 Subject 主键 */
    public @NotNull Long getSubjectId() {
        return subjectId;
    }
}
