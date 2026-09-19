package com.sca4d.auth.session;

import com.sca4d.auth.config.AuthSettings;
import com.sca4d.auth.jwt.JwtSessionProjection;
import com.sca4d.auth.session.codec.PrincipalIdLoaders;
import com.sca4d.auth.session.codec.PrincipalLoaders;
import com.sca4d.auth.session.codec.ResourceIdLoaders;
import com.sca4d.auth.session.codec.ResourceLoaders;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.authentication.AuthTokenMode;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.EstablishedSession;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.SessionLifecycle;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.authentication.SessionTokens;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 使用已经成功的认证记录建立、恢复、刷新和终止 Session。 */
public final class SessionService implements SessionLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);

    private static final SessionTerminationReason TOKEN_ISSUANCE_FAILED = new SessionTerminationReason(
            "TOKEN_ISSUANCE_FAILED");

    private final SessionIdentityLoadService identities;

    private final PrincipalLoaders principals;

    private final PrincipalIdLoaders principalIds;

    private final ResourceLoaders resources;

    private final ResourceIdLoaders resourceIds;

    private final SaTokenSessionManager sessions;

    private final JwtSessionProjection tokenProjection;

    private final Settings settings;

    private final Clock clock;

    /**
     * 创建 Session 服务。
     *
     * @param identities Session 身份装载服务
     * @param principals Principal 装载服务
     * @param principalIds 紧凑 Principal 标识装载服务
     * @param resources Resource 装载服务
     * @param resourceIds 数字 Resource 标识装载服务
     * @param sessions Sa-Token Session 管理器
     * @param tokenProjection access Token 签发和恢复服务
     * @param settings 每次建立 Session 时读取有效期的 Settings
     * @param clock 时间来源
     */
    public SessionService(
            @NotNull SessionIdentityLoadService identities,
            @NotNull PrincipalLoaders principals,
            @NotNull PrincipalIdLoaders principalIds,
            @NotNull ResourceLoaders resources,
            @NotNull ResourceIdLoaders resourceIds,
            @NotNull SaTokenSessionManager sessions,
            @NotNull JwtSessionProjection tokenProjection,
            @NotNull Settings settings,
            @NotNull Clock clock) {
        this.identities = Objects.requireNonNull(identities, "Session 身份读取服务不能为空");
        this.principals = Objects.requireNonNull(principals, "Principal 读取服务不能为空");
        this.principalIds = Objects.requireNonNull(principalIds, "Principal ID 读取服务不能为空");
        this.resources = Objects.requireNonNull(resources, "Resource 装载服务不能为空");
        this.resourceIds = Objects.requireNonNull(resourceIds, "Resource ID 装载服务不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Sa-Token Session 管理器不能为空");
        this.tokenProjection = Objects.requireNonNull(tokenProjection, "Token 投影服务不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 使用至少一条成功认证建立 Session 并签发 Token。
     *
     * @param account 已认证账号
     * @param authentications 登录步骤接受的认证记录
     * @return 新 Session 及当前 Token 模式需要返回的 Token
     * @throws IllegalArgumentException 认证列表为空或包含其他账号的记录时抛出
     */
    @CheckReturnValue
    public @NotNull EstablishedSession establish(
            @NotNull Account account,
            @NotNull List<Authentication> authentications) {
        Account checkedAccount = Objects.requireNonNull(account, "已认证账号不能为空");
        List<Authentication> checkedAuthentications = List.copyOf(Objects.requireNonNull(authentications, "认证记录不能为空"));
        if (checkedAuthentications.isEmpty()) {
            throw new IllegalArgumentException("登录必须至少完成一种认证因素");
        }
        if (checkedAuthentications.stream()
                .anyMatch(authentication -> authentication.accountId() != checkedAccount.accountId())) {
            throw new IllegalArgumentException("认证记录不属于登录账号");
        }

        // 装载账号当前有效的身份、Principal 和 Resource，再一次性建立权威 Session。
        Instant now = clock.instant();
        Duration sessionTtl = settings.get(AuthSettings.SESSION_TTL);
        long generation = sessions.currentGeneration(checkedAccount.accountId());
        SessionIdentity identity = identities.load(checkedAccount);
        List<Principal> loadedPrincipals = principals.load(identity);
        List<PrincipalIdSet<?>> loadedPrincipalIds = principalIds.load(identity);
        Session session = new Session(
                new SessionId(UUID.randomUUID().toString()),
                identity.subject(),
                identity.account(),
                loadedPrincipals,
                loadedPrincipalIds,
                checkedAuthentications,
                resources.load(loadedPrincipals),
                resourceIds.load(loadedPrincipals),
                now,
                now.plus(sessionTtl));
        String backingSessionToken = sessions.establish(session, sessionTtl, generation);
        try {
            EstablishedSession establishment = new EstablishedSession(
                    tokenProjection.issue(session, backingSessionToken),
                    session);
            LOG.info(
                    "Session 建立完成 | accountId={} principalCount={} compactPrincipalCount={} expiresAt={}",
                    checkedAccount.accountId(),
                    loadedPrincipals.size(),
                    loadedPrincipalIds.stream().mapToLong(item -> item.ids().size()).sum(),
                    session.expiresAt());
            return establishment;
        } catch (RuntimeException exception) {
            sessions.terminate(session.id(), TOKEN_ISSUANCE_FAILED);
            throw exception;
        }
    }

    /**
     * 按接口要求查找明确 Token 对应的有效 Session，不在失败时尝试另一种 Token。
     *
     * @param tokenValue 去除 Bearer 前缀的 Token
     * @param requireSessionToken 是否强制在线读取不透明 Session Token
     * @return Token 类型与接口要求一致且有效时返回 Session，否则返回空结果
     */
    @CheckReturnValue
    @ApiStatus.Internal
    public @NotNull Optional<Session> currentSession(
            @NotNull String tokenValue,
            boolean requireSessionToken) {
        String checkedToken = Objects.requireNonNull(tokenValue, "Token 不能为空");
        if (requireSessionToken || tokenProjection.mode() == AuthTokenMode.SESSION) {
            return sessions.current(checkedToken);
        }
        return tokenProjection.restore(checkedToken);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Optional<Session> resolveToken(@NotNull String tokenValue) {
        return currentSession(Objects.requireNonNull(tokenValue, "Token 不能为空"), true);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Optional<Session> resolve(@NotNull SessionId sessionId) {
        return sessions.current(Objects.requireNonNull(sessionId, "Session 标识不能为空"));
    }

    /**
     * 在线恢复 Session 并签发当前模式的 access Token，不延长 Session 有效期。
     *
     * @param sessionToken 不透明 Session Token
     * @return 当前 Token 模式需要交付的 Token
     * @throws BusinessException Session Token 无效时抛出认证失败
     */
    @CheckReturnValue
    public @NotNull SessionTokens refreshTokens(@NotNull String sessionToken) {
        String checkedToken = Objects.requireNonNull(sessionToken, "Session Token 不能为空");
        Session session = sessions.current(checkedToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED));
        return tokenProjection.issue(session, checkedToken);
    }

    /**
     * 终止指定 Session 的权威登录状态。
     *
     * @param session 要终止的 Session
     * @param reason 终止原因
     */
    public void terminate(
            @NotNull Session session,
            @NotNull SessionTerminationReason reason) {
        Session checked = Objects.requireNonNull(session, "Session 不能为空");
        terminate(checked.id(), reason);
    }

    /** {@inheritDoc} */
    @Override
    public void terminate(
            @NotNull SessionId sessionId,
            @NotNull SessionTerminationReason reason) {
        sessions.terminate(
                Objects.requireNonNull(sessionId, "Session 标识不能为空"),
                Objects.requireNonNull(reason, "Session 终止原因不能为空"));
    }

}
