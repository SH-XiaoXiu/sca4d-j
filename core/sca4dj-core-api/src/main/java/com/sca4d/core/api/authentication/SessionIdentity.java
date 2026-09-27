package com.sca4d.core.api.authentication;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;

import java.util.Objects;

/** 保存认证完成后恢复出的 Subject 和 Account 身份信息。 */
@SuppressWarnings("ClassCanBeRecord")
public class SessionIdentity {

    private final Subject subject;

    private final Account account;

    /**
     * 创建建立 Session 所需的身份快照。
     *
     * @param subject 恢复出的 Subject
     * @param account 恢复出的 Account
     * @throws NullPointerException 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException Account 不属于 Subject 时抛出
     */
    public SessionIdentity(Subject subject, Account account) {
        this.subject = Objects.requireNonNull(subject, "Subject 不能为空");
        this.account = Objects.requireNonNull(account, "Account 不能为空");
        if (subject.subjectId() != account.subjectId()) {
            throw new IllegalArgumentException("SessionIdentity Account 不属于当前 Subject");
        }
    }

    /**
     * 读取登录账号所属的接入者。
     *
     * @return 建立 Session 时恢复出的 Subject
     */
    public Subject subject() {
        return subject;
    }

    /**
     * 读取本次建立 Session 使用的登录账号。
     *
     * @return 建立 Session 时恢复出的 Account
     */
    public Account account() {
        return account;
    }

}
