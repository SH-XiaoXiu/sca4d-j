package com.sca4d.iam.identity;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/** 登记系统 Principal 类型，并按数据库确认的稳定身份读取序列化引用。 */
public final class SystemPrincipalCodec implements PrincipalCodec<SystemPrincipal> {

    /** IAM 系统 Principal 的固定类型，不表达实例或能力。 */
    public static final @NotNull TreeCode TYPE = TreeCode.ofColon("IAM:SYSTEM_PRINCIPAL");

    @Override
    public @NotNull TreeCode type() {
        return TYPE;
    }

    @Override
    public @NotNull Class<SystemPrincipal> valueType() {
        return SystemPrincipal.class;
    }

    @Override
    public byte @NotNull [] encode(@NotNull SystemPrincipal value) {
        if (!SystemPrincipals.require(value.code()).equals(value)) {
            throw new IllegalArgumentException("系统身份与数据库已确认的 ID 不一致");
        }
        return value.code().format(COLON).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public @NotNull SystemPrincipal decode(byte @NotNull [] payload) {
        String code = new String(payload, StandardCharsets.UTF_8);
        return SystemPrincipals.require(TreeCode.of(code));
    }
}
