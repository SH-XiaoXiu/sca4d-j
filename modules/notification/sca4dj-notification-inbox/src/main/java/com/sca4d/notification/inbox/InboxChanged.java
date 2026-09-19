package com.sca4d.notification.inbox;

import org.jetbrains.annotations.Contract;

/** 通知在线浏览器重新读取指定 Account 收件箱。 */
public final class InboxChanged {

    private final long accountId;

    /** @param accountId 收件箱所属 Account */
    public InboxChanged(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        this.accountId = accountId;
    }

    /** @return 收件箱所属 Account */
    @Contract(pure = true)
    public long accountId() {
        return accountId;
    }
}
