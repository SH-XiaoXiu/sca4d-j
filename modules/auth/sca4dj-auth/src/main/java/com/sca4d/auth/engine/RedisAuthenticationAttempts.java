package com.sca4d.auth.engine;

import com.sca4d.auth.config.AuthSettings;
import com.sca4d.auth.engine.attempt.AuthenticationAttemptState;
import com.sca4d.auth.engine.attempt.RedisAuthenticationAttemptStore;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationAttempts;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.authentication.AuthenticationProgress;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.authentication.AuthenticationResult;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 使用认证方法目录、Redis 尝试和应用动作策略编排零到三因素认证。
 */
public class RedisAuthenticationAttempts implements AuthenticationAttempts {

    private static final Logger LOG = LoggerFactory.getLogger(RedisAuthenticationAttempts.class);

    private final AuthenticationMethods methods;

    private final RedisAuthenticationAttemptStore attempts;

    private final AuthenticationPolicy policy;

    private final AuthSettings settings;

    private final Clock clock;

    /**
     * 创建默认认证引擎。
     *
     * @param methods  应用安装的认证方法
     * @param attempts Redis 尝试存储
     * @param policy   具体应用提供的动作认证策略
     * @param settings 每次开始认证时读取尝试有效期的 Settings
     * @param clock    时间来源
     */
    public RedisAuthenticationAttempts(
            @NotNull AuthenticationMethods methods,
            @NotNull RedisAuthenticationAttemptStore attempts,
            @NotNull AuthenticationPolicy policy,
            @NotNull AuthSettings settings,
            @NotNull Clock clock) {
        this.methods = Objects.requireNonNull(methods, "认证方法目录不能为空");
        this.attempts = Objects.requireNonNull(attempts, "认证尝试存储不能为空");
        this.policy = Objects.requireNonNull(policy, "动作认证策略不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 使用首个认证材料识别账号，并按已识别账号的应用策略开始动作认证。
     */
    @Override
    public @NotNull AuthenticationProgress start(
            @NotNull AuthenticationActionCode actionCode,
            @NotNull AuthenticationSubmission<?> submission) {
        AuthenticationActionCode checkedAction = Objects.requireNonNull(actionCode, "认证动作编码不能为空");
        AuthenticationSubmission<?> checkedSubmission = Objects.requireNonNull(submission, "认证材料不能为空");
        VerifiedAuthentication verification = methods.authenticate(checkedSubmission);
        Account account = verification.account();

        // 账号只能在首个材料验证成功后确定；确定后仍按空进度检查当前方法是否被应用策略允许。
        Set<TreeCode> initiallyAllowed = policy.allowedMethods(account, checkedAction, List.of());
        if (!methods.available(account, initiallyAllowed).contains(checkedSubmission.methodCode())) {
            throw new IllegalArgumentException("认证方法不允许用于当前动作");
        }

        Instant now = clock.instant();
        Duration attemptTtl = settings.ATTEMPT_TTL.get();
        AuthenticationAttemptState state = new AuthenticationAttemptState(
                new AuthenticationAttemptId(UUID.randomUUID().toString()),
                account,
                checkedAction,
                List.of(),
                List.of(verification),
                now.plus(attemptTtl),
                false);
        AuthenticationProgress progress = evaluate(state, now);
        attempts.create(state);
        LOG.trace(
                "首个认证材料已创建认证尝试 | attemptId={} accountId={} actionCode={} status={}",
                state.id().value(),
                account.accountId(),
                checkedAction,
                progress.satisfied());
        return progress;
    }

    /**
     * 为已知账号开始动作认证，并只在策略明确允许时复用已有认证。
     */
    @Override
    public @NotNull AuthenticationProgress start(
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode,
            @NotNull List<Authentication> reusableAuthentications) {
        Instant now = clock.instant();
        Duration attemptTtl = settings.ATTEMPT_TTL.get();
        AuthenticationAttemptState state = new AuthenticationAttemptState(
                new AuthenticationAttemptId(UUID.randomUUID().toString()),
                Objects.requireNonNull(account, "认证账号不能为空"),
                Objects.requireNonNull(actionCode, "认证动作编码不能为空"),
                Objects.requireNonNull(reusableAuthentications, "可复用认证记录不能为空"),
                List.of(),
                now.plus(attemptTtl),
                false);
        AuthenticationProgress progress = evaluate(state, now);
        attempts.create(state);
        LOG.trace(
                "已知账号已创建认证尝试 | attemptId={} accountId={} actionCode={} status={}",
                state.id().value(),
                state.account().accountId(),
                state.actionCode(),
                progress.satisfied());
        return progress;
    }

    /**
     * 在 Redis 租约内验证并保存一个认证材料，再重新评估应用策略。
     */
    @Override
    public @NotNull AuthenticationProgress submit(
            @NotNull AuthenticationAttemptId attemptId,
            @NotNull AuthenticationSubmission<?> submission) {
        AuthenticationAttemptId checkedId = Objects.requireNonNull(attemptId, "认证尝试标识不能为空");
        AuthenticationSubmission<?> checkedSubmission = Objects.requireNonNull(submission, "认证材料不能为空");
        return attempts.withLease(checkedId, () -> {
            AuthenticationAttemptState state = attempts.loadById(checkedId);
            if (state.satisfied()) {
                throw new IllegalStateException("认证尝试已经满足，不能继续提交");
            }

            Instant now = clock.instant();
            List<Authentication> completed = currentAuthentications(state, now);
            Set<TreeCode> allowed = policy
                    .allowedMethods(state.account(), state.actionCode(), completed);
            if (!methods.available(state.account(), allowed).contains(checkedSubmission.methodCode())) {
                throw new IllegalArgumentException("认证方法不允许用于当前动作");
            }

            VerifiedAuthentication verification = methods.authenticate(state.account(), checkedSubmission);
            if (state.account().accountId() != verification.account().accountId()) {
                throw new IllegalArgumentException("认证结果不属于尝试账号");
            }
            state.put(verification);
            AuthenticationProgress progress = evaluate(state, clock.instant());
            attempts.save(state);
            LOG.trace(
                    "认证尝试已推进 | attemptId={} method={} status={} remainingFactorCount={}",
                    state.id().value(),
                    verification.authentication().methodCode(),
                    progress.satisfied(),
                    progress.remainingFactorCount());
            return progress;
        });
    }

    /**
     * 在尝试租约内读取已经确定且仍然有效的账号，避免与同一尝试的提交或消费并发。
     */
    @Override
    public @NotNull Account account(@NotNull AuthenticationAttemptId attemptId) {
        AuthenticationAttemptId checkedId = Objects.requireNonNull(attemptId, "认证尝试标识不能为空");
        return attempts.withLease(checkedId, () -> attempts.loadById(checkedId).account());
    }

    /**
     * 单次消费满足当前应用策略的动作认证结果。
     */
    @Override
    public @NotNull AuthenticationResult consume(@NotNull AuthenticationAttemptId attemptId) {
        AuthenticationAttemptId checkedId = Objects.requireNonNull(attemptId, "认证尝试标识不能为空");
        return attempts.withLease(checkedId, () -> {
            AuthenticationAttemptState state = attempts.loadById(checkedId);
            Instant now = clock.instant();
            AuthenticationProgress progress = evaluate(state, now);
            if (!progress.satisfied()) {
                attempts.save(state);
                throw new IllegalStateException("认证尝试尚未满足当前动作策略");
            }
            List<Authentication> authentications = currentAuthentications(state, now);
            AuthenticationResult completion = new AuthenticationResult(
                    state.account(),
                    state.actionCode(),
                    authentications);
            attempts.delete(checkedId);
            LOG.trace(
                    "认证尝试已消费 | attemptId={} accountId={} actionCode={} authenticationCount={}",
                    state.id().value(),
                    state.account().accountId(),
                    state.actionCode(),
                    authentications.size());
            return completion;
        });
    }

    /**
     * 按当前策略计算剩余因素和下一步可提交的方法，并同步更新状态。
     */
    private AuthenticationProgress evaluate(AuthenticationAttemptState state, Instant now) {
        List<Authentication> completed = currentAuthentications(state, now);
        AuthenticationRequirement requirement = policy.requirementFor(state.account(), state.actionCode());

        Set<AuthenticationFactorKind> verifiedKinds = EnumSet.noneOf(AuthenticationFactorKind.class);
        completed.forEach(authentication -> verifiedKinds.addAll(authentication.factorKinds()));
        int remaining = Math.max(0, requirement.factorCount() - verifiedKinds.size());
        state.satisfied(remaining == 0);

        List<TreeCode> available = state.satisfied()
                ? List.of()
                : methods.available(
                        state.account(),
                        policy.allowedMethods(state.account(), state.actionCode(), completed));
        if (!state.satisfied() && available.isEmpty()) {
            throw new IllegalStateException("当前安装的认证方法无法满足动作认证策略");
        }
        return new AuthenticationProgress(
                state.id(),
                remaining,
                available,
                state.expiresAt());
    }

    /**
     * 复核本次尝试的新证据，并按策略筛选允许复用的 Session 认证记录。
     */
    private List<Authentication> currentAuthentications(AuthenticationAttemptState state, Instant now) {
        List<Authentication> current = new ArrayList<>();
        AuthenticationRequirement requirement = policy.requirementFor(state.account(), state.actionCode());
        requirement.reusableAuthenticationMaximumAge().ifPresent(maximumAge -> {
            Instant oldestAccepted = now.minus(maximumAge);
            for (Authentication authentication : state.reusableAuthentications()) {
                if (!authentication.authenticatedAt().isBefore(oldestAccepted)
                        && !authentication.authenticatedAt().isAfter(now)) {
                    current.add(authentication);
                }
            }
        });
        for (VerifiedAuthentication verification : state.verifications()) {
            if (verification.authentication().authenticatedAt().isAfter(now)) {
                throw new IllegalStateException("认证完成时间不能晚于当前时间");
            }
            methods.verifyCurrent(state.account(), verification);
            current.add(verification.authentication());
        }
        return List.copyOf(current);
    }
}
