package com.sca4d.test;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用 Session。
 */
public class TestSession implements Session {

    private final String sessionId;
    private final String tokenValue;
    private final Subject subject;
    private final Account account;
    private final List<Principal> principals;
    private final Map<Long, SessionPermission> permissions;
    private final AtomicInteger invalidationCount = new AtomicInteger();

    public TestSession(@NotNull String sessionId, @NotNull String tokenValue, @NotNull Subject subject) {
        Principal principal = Principal.fromSubject(
                subject.getSubjectId(), PrincipalType.DEFAULT, null, subject.getDisplayName());
        this.sessionId = sessionId;
        this.tokenValue = tokenValue;
        this.subject = subject;
        this.account = new Account(subject.getSubjectId(), subject.getSubjectId(), AccountScopeType.GLOBAL, null);
        this.principals = List.of(principal);
        this.permissions = Map.of(
                subject.getSubjectId(), new SessionPermission(subject.getSubjectId(), null, null));
    }

    /**
     * 创建包含指定完整状态的测试 Session。
     *
     * @param sessionId 测试会话标识
     * @param tokenValue 测试 token
     * @param subject 测试登录 Subject
     * @param account 测试登录 Account
     * @param principals 测试可选择 Principal
     * @param permissions 测试 Principal 权限
     */
    public TestSession(
            @NotNull String sessionId,
            @NotNull String tokenValue,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        this.sessionId = sessionId;
        this.tokenValue = tokenValue;
        this.subject = subject;
        this.account = account;
        this.principals = List.copyOf(principals);
        this.permissions = Map.copyOf(permissions);
    }

    /** @return 使用指定 Subject 主键且不包含 Principal 和权限的测试 Session */
    public static @NotNull TestSession of(@NotNull Long subjectId) {
        return new TestSession(
                "session-" + subjectId,
                "token-" + subjectId,
                new Subject(subjectId, "Subject " + subjectId));
    }

    /**
     * 创建携带指定完整状态的测试 Session。
     *
     * @param state 测试需要的 Subject、Principal 和权限快照
     * @return 使用固定测试标识和 token 的 Session
     */
    public static @NotNull TestSession withAuthorization(
            @NotNull Subject subject,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        return new TestSession(
                "session-" + subject.getSubjectId(),
                "token-" + subject.getSubjectId(),
                subject,
                new Account(subject.getSubjectId(), subject.getSubjectId(), AccountScopeType.GLOBAL, null),
                principals,
                permissions);
    }

    @Override
    public @NotNull String getSessionId() {
        return sessionId;
    }

    @Override
    public @NotNull String getTokenName() {
        return "Authorization";
    }

    @Override
    public @NotNull String getTokenValue() {
        return tokenValue;
    }

    @Override
    public @NotNull Subject getSubject() {
        return subject;
    }

    @Override
    public @NotNull Account getAccount() {
        return account;
    }

    @Override
    public @NotNull List<Principal> getPrincipals() {
        return principals;
    }

    @Override
    public @NotNull Map<Long, SessionPermission> getPermissions() {
        return permissions;
    }

    @Override
    public @Nullable String getDeviceId() {
        return null;
    }

    @Override
    public @Nullable String getTerminalName() {
        return null;
    }

    @Override
    public @Nullable Instant getLoginTime() {
        return null;
    }

    @Override
    public @Nullable Instant getExpireTime() {
        return null;
    }

    @Override
    public void invalidate() {
        invalidationCount.incrementAndGet();
    }

    public int getInvalidationCount() {
        return invalidationCount.get();
    }

}
