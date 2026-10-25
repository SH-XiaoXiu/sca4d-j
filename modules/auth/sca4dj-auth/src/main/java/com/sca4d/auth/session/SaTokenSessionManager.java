package com.sca4d.auth.session;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.authentication.exception.SessionIdentityUnavailableException;
import com.sca4d.core.api.permission.OperationCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 使用 Sa-Token Token-Session 保存 Core Session，并在失效代次或 Operation 目录变化后延迟刷新。 */
@SuppressWarnings("LoggingSimilarMessage")
public class SaTokenSessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SaTokenSessionManager.class);

    static final String INVALIDATION_GENERATION_ATTRIBUTE = "sca4dj.session.invalidation-generation";

    static final String LOADED_GENERATION_ATTRIBUTE = "sca4dj.session.loaded-generation";

    static final String OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE = "sca4dj.session.operation-catalog-fingerprint";

    static final String SESSION_ATTRIBUTE = "sca4dj.session";

    private final SessionRefreshService refresh;

    private final Clock clock;

    private final RedisSessionGeneration generations;

    private final RedisSessionTokenIndex tokenIndex;

    private final SessionCodec codec;

    private final StpLogic stpLogic;

    private final @NotNull String operationCatalogFingerprint;

    /**
     * 创建 Sa-Token Session 管理器。
     *
     * @param codec Core Session JSON 编解码器
     * @param refresh 身份或 Resource 变化后的 Session 刷新服务
     * @param generations Account Session 失效代次存储
     * @param operations 当前应用安装的 Operation 目录
     * @param tokenIndex SessionId 到不透明 Token 的 Redis 索引
     * @param stpLogic Sa-Token 登录和 Token-Session 操作
     * @param clock 判断 Session 有效期的时钟
     */
    public SaTokenSessionManager(
            @NotNull SessionCodec codec,
            @NotNull SessionRefreshService refresh,
            @NotNull RedisSessionGeneration generations,
            @NotNull OperationCatalog operations,
            @NotNull RedisSessionTokenIndex tokenIndex,
            @NotNull StpLogic stpLogic,
            @NotNull Clock clock) {
        this.codec = Objects.requireNonNull(codec, "Session 编解码器不能为空");
        this.refresh = Objects.requireNonNull(refresh, "Session 刷新服务不能为空");
        this.generations = Objects.requireNonNull(generations, "Session 失效代次不能为空");
        this.operationCatalogFingerprint = Objects.requireNonNull(operations, "Operation 目录不能为空").fingerprint();
        this.tokenIndex = Objects.requireNonNull(tokenIndex, "Session Token 索引不能为空");
        this.stpLogic = Objects.requireNonNull(stpLogic, "Sa-Token 登录逻辑不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 创建 Sa-Token 登录状态，并保存完整 Core Session 和当前失效代次。
     *
     * <p>调用前若 Account 的失效代次已经不等于 {@code expectedGeneration}，直接抛出异常且不写入任何状态。
     * 登录状态创建后，若编码、写入 Token-Session 或 SessionId 索引失败，或写入后再次检查发现代次已变化，
     * 方法会注销刚创建的 Sa-Token 登录状态、删除 SessionId 索引，再抛出原异常。
     *
     * @param session 已经完成身份和 Resource 装载的 Session
     * @param ttl Token 和 Token-Session 有效期
     * @param expectedGeneration 身份和 Resource 装载前读取到的 Account Session 失效代次
     * @return 新建在线 Session 的不透明 Token
     * @throws SessionIdentityUnavailableException Account 在装载期间被失效，即当前失效代次不等于
     *                                             {@code expectedGeneration}，此时不返回 Token
     */
    @CheckReturnValue
    public @NotNull String establish(
            @NotNull Session session,
            @NotNull Duration ttl,
            long expectedGeneration) {
        Session checked = Objects.requireNonNull(session, "Session 不能为空");
        Duration checkedTtl = Objects.requireNonNull(ttl, "Session 有效期不能为空");
        long accountId = checked.account().accountId();
        if (generations.current(accountId) != expectedGeneration) {
            throw new SessionIdentityUnavailableException(new IllegalStateException("Session 建立期间 Account 已失效"));
        }

        // 先由 Sa-Token 创建 Token-Session，再写入编码后的 Core Session 和代次。
        stpLogic.login(
                accountId,
                SaLoginParameter.create().setTimeout(checkedTtl.toSeconds()));
        try {
            String tokenValue = Objects.requireNonNull(stpLogic.getTokenValue(), "Sa-Token 未生成 Token");
            SaSession tokenSession = stpLogic.getTokenSession();
            tokenSession.set(SESSION_ATTRIBUTE, codec.encode(checked));
            tokenSession.set(INVALIDATION_GENERATION_ATTRIBUTE, expectedGeneration);
            tokenSession.set(LOADED_GENERATION_ATTRIBUTE, expectedGeneration);
            tokenSession.set(OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE, operationCatalogFingerprint);
            tokenIndex.save(checked.id(), tokenValue, checkedTtl);
            if (generations.current(accountId) != expectedGeneration) {
                throw new SessionIdentityUnavailableException(new IllegalStateException("Session 建立期间 Account 已失效"));
            }
            return tokenValue;
        } catch (RuntimeException exception) {
            // 编码或写入失败时立即注销刚创建的登录状态，避免只留下 Sa-Token 会话。
            stpLogic.logout();
            tokenIndex.delete(checked.id());
            throw exception;
        }
    }

    /**
     * 读取 Account 当前 Session 失效代次。
     *
     * <p>登录流程必须在读取身份和 Resource 前保存该值，并在建立 backing Session 时原样传回。
     *
     * @param accountId 待建立 Session 的 Account
     * @return 当前非负失效代次
     */
    @CheckReturnValue
    public long currentGeneration(long accountId) {
        return generations.current(accountId);
    }

    /**
     * 从当前请求中的 Sa-Token Token-Session 读取 Session。
     *
     * @return 有效或刷新成功的 Session；Token 无效时为空
     */
    @CheckReturnValue
    public @NotNull Optional<Session> current() {
        try {
            // 带有 Token 但 Sa-Token 未登录时清理残留登录状态。
            if (!stpLogic.isLogin()) {
                String tokenValue = stpLogic.getTokenValue();
                if (tokenValue != null && !tokenValue.isBlank()) {
                    stpLogic.logout();
                }
                return Optional.empty();
            }
            long loginId;
            try {
                loginId = stpLogic.getLoginIdAsLong();
            } catch (NumberFormatException exception) {
                stpLogic.logout();
                return Optional.empty();
            }
            return restore(stpLogic.getTokenSession(), loginId, stpLogic::logout);
        } catch (NotLoginException exception) {
            stpLogic.logout();
            return Optional.empty();
        }
    }

    /**
     * 直接按 Token 值读取 Session，不读取当前 HTTP 请求中的 Token 来源。
     *
     * @param tokenValue 已去除 Bearer 前缀的 Token 值
     * @return 有效或刷新成功的 Session；Token 无效时为空
     */
    @CheckReturnValue
    public @NotNull Optional<Session> current(@NotNull String tokenValue) {
        String checkedToken = Objects.requireNonNull(tokenValue, "Token 不能为空");
        if (checkedToken.isBlank()) {
            return Optional.empty();
        }
        Object loginIdValue = stpLogic.getLoginIdByToken(checkedToken);
        if (loginIdValue == null) {
            return Optional.empty();
        }
        long loginId;
        try {
            loginId = Long.parseLong(loginIdValue.toString());
        } catch (NumberFormatException exception) {
            stpLogic.logoutByTokenValue(checkedToken);
            return Optional.empty();
        }
        SaSession tokenSession = stpLogic.getTokenSessionByToken(checkedToken, false);
        if (tokenSession == null) {
            stpLogic.logoutByTokenValue(checkedToken);
            return Optional.empty();
        }
        return restore(tokenSession, loginId, () -> stpLogic.logoutByTokenValue(checkedToken));
    }

    /**
     * 按 SessionId 索引找到不透明 Token，再读取仍在线且已经完成失效刷新检查的 Session。
     *
     * @param sessionId Session 标识
     * @return Session 已终止、过期或索引不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<Session> current(@NotNull SessionId sessionId) {
        return tokenIndex.find(Objects.requireNonNull(sessionId, "Session 标识不能为空"))
                .flatMap(this::current);
    }

    /**
     * 按 Session 标识注销对应 Token 登录，并删除 Token 索引。
     *
     * <p>索引不存在时（Token 已注销、已过期或从未建立）不执行任何操作，也不抛出异常，只记录 TRACE 日志。
     *
     * @param sessionId Session 标识
     * @param reason 终止原因，只用于日志
     */
    public void terminate(@NotNull SessionId sessionId, @NotNull SessionTerminationReason reason) {
        terminateIndexed(
                Objects.requireNonNull(sessionId, "Session 标识不能为空"),
                Objects.requireNonNull(reason, "Session 终止原因不能为空"));
    }

    /**
     * 解码 Token-Session，并在失效代次或 Operation 目录变化时重建身份和 Resource。
     *
     * @param tokenSession Sa-Token Token-Session
     * @param loginId Sa-Token 保存的 Account 标识
     * @param invalidToken Token 数据不完整时执行的清理操作
     * @return 有效或刷新成功的 Session；Token 数据无效时为空
     */
    private Optional<Session> restore(
            SaSession tokenSession,
            long loginId,
            Runnable invalidToken) {
        Object encoded = tokenSession.get(SESSION_ATTRIBUTE);
        Object invalidationValue = tokenSession.get(INVALIDATION_GENERATION_ATTRIBUTE);
        Object loadedValue = tokenSession.get(LOADED_GENERATION_ATTRIBUTE);
        Object catalogFingerprintValue = tokenSession.get(OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE);
        if (!(encoded instanceof String value)
                || value.isBlank()
                || !(invalidationValue instanceof Number)
                || !(loadedValue instanceof Number)) {
            invalidToken.run();
            return Optional.empty();
        }
        Session session;
        try {
            session = codec.decode(value);
        } catch (IllegalStateException exception) {
            invalidToken.run();
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (loginId != session.account().accountId()
                || now.isBefore(session.issuedAt())
                || !now.isBefore(session.expiresAt())) {
            invalidToken.run();
            return Optional.empty();
        }

        long invalidationGeneration = generations.current(session.account().accountId());
        long loadedGeneration = ((Number) loadedValue).longValue();
        boolean generationChanged = loadedGeneration != invalidationGeneration;
        boolean operationCatalogChanged = !operationCatalogFingerprint.equals(catalogFingerprintValue);
        if (((Number) invalidationValue).longValue() != invalidationGeneration) {
            tokenSession.set(INVALIDATION_GENERATION_ATTRIBUTE, invalidationGeneration);
        }
        if (!generationChanged && !operationCatalogChanged) {
            return Optional.of(session);
        }

        long startedAt = System.nanoTime();
        try {
            Session refreshed = refresh.refresh(session);
            tokenSession.set(SESSION_ATTRIBUTE, codec.encode(refreshed));
            tokenSession.set(LOADED_GENERATION_ATTRIBUTE, invalidationGeneration);
            // 指纹最后写入；前面的 Session 或代次保存失败时，下次请求必须继续尝试刷新。
            tokenSession.set(OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE, operationCatalogFingerprint);
            LOG.info(
                    "Session 刷新完成 | sessionId={} accountId={} generation={} generationChanged={} operationCatalogChanged={} durationMs={}",
                    refreshed.id().value(),
                    refreshed.account().accountId(),
                    invalidationGeneration,
                    generationChanged,
                    operationCatalogChanged,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return Optional.of(refreshed);
        } catch (SessionIdentityUnavailableException exception) {
            invalidToken.run();
            tokenIndex.delete(session.id());
            LOG.info(
                    "Session 已注销 | sessionId={} reason={}",
                    session.id().value(),
                    SessionTerminationReason.IDENTITY_UNAVAILABLE.code());
            return Optional.empty();
        }
    }

    /**
     * 按 SessionId 查找 Token 索引，并注销该索引对应的 Sa-Token 登录状态。
     *
     * <p>索引不存在时只记录 TRACE 日志并直接返回，不注销任何 Token。索引存在时先注销该 Token，
     * 再删除 SessionId 索引，并记录 INFO 日志。
     *
     * @param sessionId Core Session 标识
     * @param reason 注销原因，只用于日志
     */
    private void terminateIndexed(SessionId sessionId, SessionTerminationReason reason) {
        Optional<String> tokenValue = tokenIndex.find(sessionId);
        if (tokenValue.isEmpty()) {
            LOG.trace(
                    "Session 注销已跳过，Token 索引不存在 | sessionId={} reason={}",
                    sessionId.value(),
                    reason.code());
            return;
        }

        // 只有仍能解析到 Token 的首次注销才删除索引并记录最终结果。
        stpLogic.logoutByTokenValue(tokenValue.get());
        tokenIndex.delete(sessionId);
        LOG.info("Session 已注销 | sessionId={} reason={}", sessionId.value(), reason.code());
    }

}
