package com.sca4d.authentication.password.audit;

import com.sca4d.common.audit.AuditPayload;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.security.SensitiveUtils;
import com.sca4d.iam.error.KnownCredentialRejectedException;
import lombok.Data;
import lombok.experimental.Accessors;

/** 密码登录成功或失败时写入审计记录的非敏感认证数据。 */
@Data
@Accessors(chain = true)
public class PasswordLoginAuditPayload implements AuditPayload {

    private CredentialType credentialType = CredentialType.PASSWORD;
    private Long credentialId;
    private Long subjectId;
    private String loginAccountMask;
    private String deviceId;
    private String terminalName;

    /**
     * 根据请求参数、成功返回值或原始认证异常构造非敏感登录审计数据。
     *
     * @param identifier 客户端提交的登录账号；只保存脱敏提示
     * @param deviceId 客户端终端设备标识
     * @param terminalName 客户端终端名称
     * @param successfulSubjectId 成功响应中的 Subject 主键；失败时为空
     * @param error 原始认证异常；成功时为空
     * @return 不包含密码和完整账号的类型化审计 Payload
     */
    public static PasswordLoginAuditPayload capture(
            String identifier,
            String deviceId,
            String terminalName,
            Long successfulSubjectId,
            Throwable error) {
        Long credentialId = null;
        Long subjectId = successfulSubjectId;
        if (error instanceof KnownCredentialRejectedException rejected) {
            credentialId = rejected.getCredentialId();
            subjectId = rejected.getSubjectId();
        }
        return new PasswordLoginAuditPayload()
                .setCredentialId(credentialId)
                .setSubjectId(subjectId)
                .setLoginAccountMask(SensitiveUtils.identifier(identifier))
                .setDeviceId(deviceId)
                .setTerminalName(terminalName);
    }
}
