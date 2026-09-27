package com.sca4d.app.server.authentication.view;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.permission.Operation;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 返回 Dashboard 所需的当前认证身份投影。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应及其嵌套对象。
public final class CurrentSessionView {

    private final @NotNull String sessionId;

    private final @NotNull Instant issuedAt;

    private final @NotNull Instant expiresAt;

    private final @NotNull SubjectView subject;

    private final @NotNull AccountView account;

    private final @NotNull List<@NotNull String> permissions;

    private CurrentSessionView(
            @NotNull String sessionId,
            @NotNull Instant issuedAt,
            @NotNull Instant expiresAt,
            @NotNull SubjectView subject,
            @NotNull AccountView account,
            @NotNull List<@NotNull String> permissions) {
        this.sessionId = sessionId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.subject = subject;
        this.account = account;
        this.permissions = List.copyOf(permissions);
    }

    /**
     * 从当前 Session 创建公开身份投影。
     *
     * @param session 当前认证 Session
     * @return 仅包含客户端所需身份字段的投影
     */
    @CheckReturnValue
    public static @NotNull CurrentSessionView from(@NotNull Session session) {
        Session checked = Objects.requireNonNull(session, "Session 不能为空");
        return new CurrentSessionView(
                checked.id().value(),
                checked.issuedAt(),
                checked.expiresAt(),
                new SubjectView(checked.subject().subjectId(), checked.subject().type().value()),
                new AccountView(checked.account().accountId(), checked.account().realmKey().value()),
                checked.principals()
                        .stream()
                        .flatMap(principal -> checked.resources(principal.principalId()).stream())
                        .filter(Operation.class::isInstance)
                        .map(Operation.class::cast)
                        .map(operation -> operation.code().format(COLON))
                        .distinct()
                        .sorted()
                        .toList());
    }

    /**
     * 返回 Session 标识。
     * @return Session 标识
     */
    public @NotNull String getSessionId() {
        return sessionId;
    }

    /**
     * 返回 Session 签发时间。
     * @return Session 签发时间
     */
    public @NotNull Instant getIssuedAt() {
        return issuedAt;
    }

    /**
     * 返回 Session 过期时间。
     * @return Session 过期时间
     */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * 返回当前 Subject。
     * @return 当前 Subject
     */
    public @NotNull SubjectView getSubject() {
        return subject;
    }

    /**
     * 返回当前 Account。
     * @return 当前 Account
     */
    public @NotNull AccountView getAccount() {
        return account;
    }

    /**
     * 返回当前 Session 中生效的权限编码。
     * @return 已去重并排序的权限编码
     */
    public @NotNull @Unmodifiable List<@NotNull String> getPermissions() {
        return permissions;
    }

    /** 当前 Session 的 Subject 字段。 */
    public static final class SubjectView {
        private final long subjectId;

        private final @NotNull String type;

        private SubjectView(long subjectId, @NotNull String type) {
            this.subjectId = subjectId;
            this.type = type;
        }

        /**
         * 返回 Subject 标识。
         * @return Subject 标识
         */
        public long getSubjectId() {
            return subjectId;
        }

        /**
         * 返回 Subject 类型。
         * @return Subject 类型
         */
        public @NotNull String getType() {
            return type;
        }
    }

    /** 当前 Session 的 Account 字段。 */
    public static final class AccountView {
        private final long accountId;

        private final @NotNull String realmKey;

        private AccountView(long accountId, @NotNull String realmKey) {
            this.accountId = accountId;
            this.realmKey = realmKey;
        }

        /**
         * 返回 Account 标识。
         * @return Account 标识
         */
        public long getAccountId() {
            return accountId;
        }

        /**
         * 返回 Account 所属 Realm。
         * @return Account 所属 Realm
         */
        public @NotNull String getRealmKey() {
            return realmKey;
        }
    }

}
