package com.sca4d.auth.passkey.credential.mapper;

import com.sca4d.core.api.concurrent.Version;

/**
 * 映射 Passkey 账号锚点保存的 user handle 和乐观版本。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PasskeyAccountState {

    private final Version version;

    private final byte[] userHandle;

    private final long accountId;

    /**
     * 创建 MyBatis 查询结果。
     */
    public PasskeyAccountState(long accountId, byte[] userHandle, Version version) {
        this.accountId = accountId;
        this.userHandle = userHandle.clone();
        this.version = version;
    }

    /**
     * 锚点所属 Account 标识。
     *
     * @return 锚点所属 Account 标识
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
     * 账号锚点的乐观版本。
     *
     * @return 账号锚点的乐观版本
     */
    public Version version() {
        return version;
    }
}
