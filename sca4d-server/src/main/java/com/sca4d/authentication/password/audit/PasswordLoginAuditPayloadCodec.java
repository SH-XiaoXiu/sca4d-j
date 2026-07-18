package com.sca4d.authentication.password.audit;

import com.sca4d.common.audit.AuditPayloadCodec;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** 密码登录审计载荷编解码契约。 */
@Component
public class PasswordLoginAuditPayloadCodec implements AuditPayloadCodec<PasswordLoginAuditPayload> {

    /** 账号密码登录审计载荷的持久化类型。 */
    public static final String PAYLOAD_TYPE = "authentication.password-login";

    @Override
    public @NotNull String payloadType() {
        return PAYLOAD_TYPE;
    }

    @Override
    public @NotNull Class<PasswordLoginAuditPayload> payloadClass() {
        return PasswordLoginAuditPayload.class;
    }
}
