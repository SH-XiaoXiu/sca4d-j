package com.sca4d.iam.base.user;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 组合单一 Platform 用户的 Subject、Account，并作为对应 Principal 使用。 */
public class User implements Principal {

    private final @NotNull Subject subject;

    private final @NotNull Account account;

    private final long principalId;

    /**
     * 创建 Platform USER 直通对象。
     *
     * @param subject Platform Subject
     * @param account Platform Account
     * @param principal Platform Principal
     * @throws IllegalArgumentException Account 不属于指定 Subject 时抛出
     */
    public User(@NotNull Subject subject, @NotNull Account account, @NotNull Principal principal) {
        Subject checkedSubject = Objects.requireNonNull(subject, "Subject 不能为空");
        Account checkedAccount = Objects.requireNonNull(account, "Account 不能为空");
        Principal checkedPrincipal = Objects.requireNonNull(principal, "Principal 不能为空");
        if (checkedSubject.subjectId() != checkedAccount.subjectId()) {
            throw new IllegalArgumentException("Account 不属于指定 Subject");
        }
        this.subject = checkedSubject;
        this.account = checkedAccount;
        this.principalId = checkedPrincipal.principalId();
    }

    /**
     * 使用任意一组 Subject、Account、Principal 构造直通对象。
     *
     * @param subject 接入者
     * @param account 属于当前接入者的账号
     * @param principal 业务主体
     * @return USER 直通对象
     */
    @CheckReturnValue
    @Contract("_, _, _ -> new")
    public static @NotNull User of(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull Principal principal) {
        return new User(subject, account, principal);
    }

    /**
     * 从已经认证的 Platform Session 构造 USER，不访问外部服务。
     *
     * @param session 当前认证 Session
     * @return Session 对应的 USER
     * @throws IllegalStateException Session 不是完整的单一 Platform 用户场景时抛出
     */
    @CheckReturnValue
    public static @NotNull User from(@NotNull Session session) {
        Session checked = Objects.requireNonNull(session, "Session 不能为空");
        Subject subject = checked.subject();
        Account account = checked.account();
        if (!PlatformIdentityService.PLATFORM_REALM.equals(account.realmKey())) {
            throw new IllegalStateException("Session Account 必须属于 platform realm");
        }
        if (checked.principals().size() != 1
                || !(checked.principals().getFirst() instanceof PlatformPrincipal principal)) {
            throw new IllegalStateException("Session 必须恰好包含一个 PlatformPrincipal，且不能包含其他 Principal");
        }
        return User.of(subject, account, principal);
    }

    /**
     * 读取 Platform 接入者标识。
     *
     * @return User 持有 Subject 的标识
     */
    @Contract(pure = true)
    public long subjectId() {
        return subject.subjectId();
    }

    /**
     * 读取 Platform 接入者类型。
     *
     * @return User 持有 Subject 的类型
     */
    @Contract(pure = true)
    public @NotNull SubjectType type() {
        return subject.type();
    }

    /**
     * 读取 Platform 登录账号标识。
     *
     * @return User 持有 Account 的标识
     */
    @Contract(pure = true)
    public long accountId() {
        return account.accountId();
    }

    /**
     * 读取 Platform 登录账号所属的认证域。
     *
     * @return User 持有 Account 的认证域
     */
    @Contract(pure = true)
    public @NotNull RealmKey realmKey() {
        return account.realmKey();
    }

    /**
     * 构造 User 时保存的接入者。
     *
     * @return User 持有的 Subject
     */
    @Contract(pure = true)
    public @NotNull Subject subject() {
        return subject;
    }

    /**
     * 构造 User 时保存的登录账号。
     *
     * @return User 持有的 Account
     */
    @Contract(pure = true)
    public @NotNull Account account() {
        return account;
    }

    /** @return Platform Principal 标识 */
    @Override
    @Contract(pure = true)
    public long principalId() {
        return principalId;
    }

    /**
     * 单一 Platform 用户场景使用的用户标识。
     *
     * @return USER 标识，与 {@link #principalId()} 相同
     */
    @Contract(pure = true)
    public long userId() {
        return principalId;
    }
}
