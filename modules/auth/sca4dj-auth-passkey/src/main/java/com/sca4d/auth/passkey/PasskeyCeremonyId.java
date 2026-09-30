package com.sca4d.auth.passkey;

import com.sca4d.core.api.utils.TextUtils;

import java.util.Objects;

/** 保存调用方在完成 WebAuthn ceremony 时必须原样回传的不透明标识。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PasskeyCeremonyId {

    private final String value;

    /** @param value 去除首尾空白后仍非空的不透明标识 */
    public PasskeyCeremonyId(String value) {
        this.value = TextUtils.requireText(value, "Passkey ceremony 标识");
    }

    /**
     * 调用方需要原样回传的值。
     *
     * @return 调用方需要原样回传的值
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PasskeyCeremonyId id && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
