package com.sca4d.auth.passkey.credential.mapper;

import com.sca4d.core.api.concurrent.Version;

/**
 * 映射 Passkey 账号锚点保存的 user handle 和乐观版本。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class PasskeyAccountState {

    private final long accountId;

    private final byte[] userHandle;

    private final Version version;

    /**
     * 创建 MyBatis 查询结果。
     */
    public PasskeyAccountState(long accountId, byte[] userHandle, Version version) {
        this.accountId = accountId;
        this.userHandle = userHandle.clone();
        this.version = version;
    }

    /**
     * 返回锚点所属 Account 标识。
     */
    public long accountId() {
        return accountId;
    }

    /**
     * 返回 WebAuthn user handle 的副本。
     */
    public byte[] userHandle() {
        return userHandle.clone();
    }

    /**
     * 返回账号锚点的乐观版本。
     */
    public Version version() {
        return version;
    }
}
