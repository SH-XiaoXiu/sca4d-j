package com.sca4d.authentication.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.SessionSnapshot;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.permission.SessionPermission;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 Sa-Token 的 Authentication 实现。
 */
@Component
@RequiredArgsConstructor
public class SaTokenAuthentication implements Authentication {

    /** Sa-Token terminal extra 中保存的项目固定会话标识。 */
    static final String SESSION_ID = "sessionId";

    /** Sa-Token terminal extra 中保存的终端展示名称。 */
    static final String TERMINAL_NAME = "terminalName";

    static final String SESSION_SUBJECT = "session.subject";
    static final String SESSION_ACCOUNT = "session.account";
    static final String SESSION_PRINCIPALS = "session.principals";
    static final String SESSION_PERMISSIONS = "session.permissions";

    private static final TypeReference<List<Principal>> PRINCIPAL_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<Long, SessionPermission>> PERMISSION_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Override
    public @NotNull Session createSession(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        return createSession(subject, account, principals, permissions, null, null);
    }

    @Override
    public @NotNull Session createSession(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions,
            @Nullable String deviceId,
            @Nullable String terminalName) {
        try {
            // 项目会话 ID 与 bearer token 分离，对外会话管理不暴露可用于认证的 token。
            String sessionId = IdUtil.fastSimpleUUID();
            SaLoginParameter parameter = SaLoginParameter.create()
                    .setDeviceId(deviceId)
                    .setTerminalExtra(SESSION_ID, sessionId)
                    .setTerminalExtra(TERMINAL_NAME, terminalName);

            validate(subject, account, principals, permissions);
            StpUtil.login(account.getAccountId(), parameter);
            writeSession(StpUtil.getSession(), subject, account, principals, permissions);

            // 从 Sa-Token 已落定的登录态创建可显式失效的项目 Session 句柄。
            Session session = currentSession();
            if (session == null) {
                throw new UnauthenticatedException(AuthenticationErrorCode.SESSION_CREATE_FAILED);
            }
            return session;
        } catch (SaTokenException exception) {
            throw new UnauthenticatedException(AuthenticationErrorCode.SESSION_CREATE_FAILED);
        }
    }

    @Override
    public void logout() {
        Session session = currentSession();
        if (session == null) {
            throw new UnauthenticatedException();
        }
        session.invalidate();
    }

    @Override
    public @Nullable Session currentSession() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            String tokenValue = StpUtil.getTokenValue();
            Long accountId = StpUtil.getLoginIdAsLong();
            SaSession accountSession = StpUtil.getSession();
            SaTerminalInfo terminal = accountSession.getTerminal(tokenValue);
            return session(accountId, tokenValue, terminal, accountSession);
        } catch (SaTokenException exception) {
            return null;
        }
    }

    @Override
    public void replaceSession(
            @NotNull Long accountId,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        validate(subject, account, principals, permissions);
        if (!accountId.equals(account.getAccountId())) {
            throw new IllegalArgumentException("替换的 Session 不属于目标 Account");
        }
        SaSession accountSession = StpUtil.getStpLogic().getSessionByLoginId(accountId, false);
        if (accountSession != null) {
            writeSession(accountSession, subject, account, principals, permissions);
        }
    }

    @Override
    public @NotNull List<Long> onlineAccountIds() {
        String prefix = StpUtil.getStpLogic().splicingKeySession("");
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (String sessionId : StpUtil.searchSessionId("", 0, -1, true)) {
            if (sessionId.startsWith(prefix)) {
                accountIds.add(Long.valueOf(sessionId.substring(prefix.length())));
            }
        }
        return List.copyOf(accountIds);
    }

    @Override
    public void endSessions(@NotNull Long accountId) {
        StpUtil.logout(accountId);
    }

    @Override
    public @NotNull List<SessionSnapshot> sessions(@NotNull Long accountId) {
        SaSession accountSession = StpUtil.getStpLogic().getSessionByLoginId(accountId, false);
        if (accountSession == null) {
            return List.of();
        }
        // Account-Session 一次读取即可获得终端快照；只从内存副本裁剪安全字段，不按终端查询 Token TTL 或其他 Redis 键。
        return accountSession.terminalListCopy().stream()
                .map(terminal -> {
                    Object sessionIdValue = terminal.getExtra(SESSION_ID);
                    if (!(sessionIdValue instanceof String sessionId) || !StringUtils.hasText(sessionId)) {
                        return null;
                    }
                    Object terminalNameValue = terminal.getExtra(TERMINAL_NAME);
                    String terminalName = terminalNameValue instanceof String value ? value : null;
                    return new SessionSnapshot(
                            sessionId,
                            accountId,
                            terminal.getDeviceId(),
                            terminalName,
                            Instant.ofEpochMilli(terminal.getCreateTime()));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SessionSnapshot::getLoginTime).reversed())
                .toList();
    }

    @Override
    public void endSession(@NotNull Long accountId, @NotNull String sessionId) {
        SaSession accountSession = StpUtil.getStpLogic().getSessionByLoginId(accountId, false);
        if (accountSession == null) {
            throw new BizException(AuthenticationErrorCode.SESSION_NOT_FOUND);
        }
        SaTerminalInfo matched = accountSession.terminalListCopy().stream()
                .filter(terminal -> sessionId.equals(terminal.getExtra(SESSION_ID)))
                .findFirst()
                .orElseThrow(() -> new BizException(AuthenticationErrorCode.SESSION_NOT_FOUND));
        // Token 只在认证实现内部用于框架级注销，不进入返回、日志、审计或调用方状态。
        StpUtil.getStpLogic().logoutByTokenValue(matched.getTokenValue());
    }

    /**
     * 将 Sa-Token 状态转换为共用 Session 句柄。
     *
     * @param accountId Account 主键
     * @param tokenValue token 值
     * @param terminal 终端信息
     * @param accountSession Subject 共享的 Account-Session
     * @return Session 句柄
     */
    private @NotNull Session session(
            @NotNull Long accountId,
            @NotNull String tokenValue,
            @Nullable SaTerminalInfo terminal,
            @NotNull SaSession accountSession) {
        Object subjectValue = accountSession.get(SESSION_SUBJECT);
        Object accountValue = accountSession.get(SESSION_ACCOUNT);
        Object principalsValue = accountSession.get(SESSION_PRINCIPALS);
        Object permissionsValue = accountSession.get(SESSION_PERMISSIONS);
        if (subjectValue == null || accountValue == null || principalsValue == null || permissionsValue == null) {
            throw new UnauthenticatedException(AuthenticationErrorCode.SESSION_AUTHORIZATION_INCOMPLETE);
        }
        Subject subject = objectMapper.convertValue(subjectValue, Subject.class);
        Account account = objectMapper.convertValue(accountValue, Account.class);
        List<Principal> principals = List.copyOf(objectMapper.convertValue(principalsValue, PRINCIPAL_LIST));
        Map<Long, SessionPermission> permissions = Map.copyOf(objectMapper.convertValue(permissionsValue, PERMISSION_MAP));
        validate(subject, account, principals, permissions);
        if (!accountId.equals(account.getAccountId())) {
            throw new UnauthenticatedException(AuthenticationErrorCode.SESSION_ACCOUNT_MISMATCH);
        }
        Object sessionIdValue = terminal == null ? null : terminal.getExtra(SESSION_ID);
        if (!(sessionIdValue instanceof String sessionId) || !StringUtils.hasText(sessionId)) {
            throw new UnauthenticatedException(AuthenticationErrorCode.SESSION_ID_MISSING);
        }
        Object terminalNameValue = terminal == null ? null : terminal.getExtra(TERMINAL_NAME);
        String terminalName = terminalNameValue instanceof String value ? value : null;
        Instant loginTime = terminal == null ? null : Instant.ofEpochMilli(terminal.getCreateTime());
        long timeoutSeconds = StpUtil.getTokenTimeout(tokenValue);
        Instant expireTime = timeoutSeconds > 0 ? Instant.now().plusSeconds(timeoutSeconds) : null;
        return new SaTokenSession(
                sessionId,
                StpUtil.getTokenName(),
                tokenValue,
                subject,
                account,
                principals,
                permissions,
                terminal == null ? null : terminal.getDeviceId(),
                terminalName,
                loginTime,
                expireTime);
    }

    /**
     * 将完整 Subject 会话数据写入共享 Account-Session。
     *
     * @param accountSession Sa-Token Account-Session
     * @param subject 登录 Subject
     * @param account 登录 Account
     * @param principals 当前可选择的全部 Principal
     * @param permissions 每个 Principal 的角色和权限
     */
    private void writeSession(
            @NotNull SaSession accountSession,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        accountSession.set(SESSION_SUBJECT, subject);
        accountSession.set(SESSION_ACCOUNT, account);
        accountSession.set(SESSION_PRINCIPALS, List.copyOf(principals));
        accountSession.set(SESSION_PERMISSIONS, Map.copyOf(permissions));
    }

    /**
     * 校验 Session 中 Subject、Account、Principal 和权限的归属关系。
     *
     * @param subject 登录 Subject
     * @param account 登录 Account
     * @param principals 当前可选择的全部 Principal
     * @param permissions 每个 Principal 的角色和权限
     */
    private void validate(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        if (!subject.getSubjectId().equals(account.getSubjectId())
                || (account.getScopeType() == AccountScopeType.GLOBAL && account.getScopeId() != null)
                || (account.getScopeType() == AccountScopeType.TENANT && account.getScopeId() == null)) {
            throw new IllegalArgumentException("Session Account 与 Subject 或作用域不匹配");
        }
        Set<Long> principalIds = new HashSet<>();
        for (Principal principal : principals) {
            Long principalId = principal.getPrincipalId();
            if (principalId == null
                    || principal.getPrincipalType() == null
                    || !subject.getSubjectId().equals(principal.getSubjectId())
                    || (account.getScopeType() == AccountScopeType.TENANT
                            && principal.getPrincipalType() != PrincipalType.DEFAULT
                            && (principal.getPrincipalType() != PrincipalType.TENANT
                                    || !account.getScopeId().equals(principal.getTenantId())))
                    || !principalIds.add(principalId)) {
                throw new IllegalArgumentException("Session Principal 与 Subject 不匹配");
            }
            SessionPermission permission = permissions.get(principalId);
            if (permission == null || !principalId.equals(permission.getPrincipalId())) {
                throw new IllegalArgumentException("Session 缺少 Principal 授权");
            }
        }
        if (!principalIds.equals(permissions.keySet())) {
            throw new IllegalArgumentException("Session 包含不可选择 Principal 的授权");
        }
    }

}
