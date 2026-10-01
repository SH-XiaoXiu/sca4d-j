package com.sca4d.auth.federation;

import com.sca4d.core.api.identity.Account;

import java.util.Objects;

/** 具体应用解析外部身份后返回的本地账号和绑定版本。 */
public class FederatedAccountResolution {
    private final Account account;

    private final long identityId;

    private final long identityVersion;

    /** 创建解析结果。 */
    public FederatedAccountResolution(Account account, long identityId, long identityVersion) {
        this.account = Objects.requireNonNull(account, "Account 不能为空");
        if (identityId <= 0 || identityVersion < 0)
            throw new IllegalArgumentException("外部身份版本无效");
        this.identityId = identityId;
        this.identityVersion = identityVersion;
    }

    /** @return 本地 Account */
    public Account account() {
        return account;
    }

    /** @return 外部身份标识 */
    public long identityId() {
        return identityId;
    }

    /** @return 外部身份版本 */
    public long identityVersion() {
        return identityVersion;
    }
}
