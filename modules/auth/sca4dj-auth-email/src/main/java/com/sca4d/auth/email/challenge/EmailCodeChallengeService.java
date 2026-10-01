package com.sca4d.auth.email.challenge;

import static com.sca4d.auth.email.challenge.EmailCodeChallengeStatus.CONSUMED;
import static com.sca4d.auth.email.challenge.EmailCodeChallengeStatus.PENDING;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.email.EmailCodeChallenge;
import com.sca4d.auth.email.EmailCodeChallengeId;
import com.sca4d.auth.email.EmailCodeErrorCode;
import com.sca4d.auth.email.EmailCodeNotice;
import com.sca4d.auth.email.EmailCodeProof;
import com.sca4d.auth.email.config.EmailCodeSettings;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.security.SecretHasher;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 签发匿名一致的 Challenge，异步登记通知，并原子验证 Argon2id 验证码摘要。 */
public class EmailCodeChallengeService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailCodeChallengeService.class);

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private final @NotNull AccountDirectory accounts;

    private final @NotNull Clock clock;

    private final @NotNull EmailCodeSettings settings;

    private final @NotNull Notifications notifications;

    private final @NotNull RealmKey accountRealm;

    private final @NotNull RedisEmailCodeChallengeStore store;

    private final @NotNull SecretHasher hasher;

    private final @NotNull SecureRandom random;

    private final @NotNull String dummyHash;

    /**
     * @param accounts 使用 EMAIL 类型解析和复核 Account 的公共目录
     * @param accountRealm Email 登录使用的认证域
     * @param notifications 可靠异步通知入口
     * @param store Redis Challenge 存储
     * @param hasher Argon2id 验证码摘要
     * @param settings Challenge 设置
     * @param clock 时间来源
     */
    public EmailCodeChallengeService(
            @NotNull AccountDirectory accounts,
            @NotNull RealmKey accountRealm,
            @NotNull Notifications notifications,
            @NotNull RedisEmailCodeChallengeStore store,
            @NotNull SecretHasher hasher,
            @NotNull EmailCodeSettings settings,
            @NotNull Clock clock) {
        this(accounts, accountRealm, notifications, store, hasher, settings, clock, new SecureRandom());
    }

    /** 使用可替换随机源创建测试可控的 Challenge 服务。 */
    EmailCodeChallengeService(
            @NotNull AccountDirectory accounts,
            @NotNull RealmKey accountRealm,
            @NotNull Notifications notifications,
            @NotNull RedisEmailCodeChallengeStore store,
            @NotNull SecretHasher hasher,
            @NotNull EmailCodeSettings settings,
            @NotNull Clock clock,
            @NotNull SecureRandom random) {
        this.accounts = Objects.requireNonNull(accounts, "AccountDirectory 不能为空");
        this.accountRealm = Objects.requireNonNull(accountRealm, "Email Account Realm 不能为空");
        this.notifications = Objects.requireNonNull(notifications, "NotificationService 不能为空");
        this.store = Objects.requireNonNull(store, "RedisEmailCodeChallengeStore 不能为空");
        this.hasher = Objects.requireNonNull(hasher, "SecretHasher 不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
        this.random = Objects.requireNonNull(random, "SecureRandom 不能为空");
        this.dummyHash = hasher.hash(generateCode());
    }

    /**
     * 按匿名调用方提交的邮箱签发登录 Challenge。未知邮箱和不可投递邮箱返回相同响应结构。
     *
     * @param email 原始邮箱登录标识
     * @return 不暴露邮箱是否存在的 Challenge
     */
    @Transactional
    @CheckReturnValue
    public @NotNull EmailCodeChallenge issue(@NotNull String email) {
        String rawEmail = Objects.requireNonNull(email, "Email 登录标识不能为空");
        Account account = accounts.resolve(
                accountRealm,
                EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE,
                rawEmail)
                .orElse(null);
        String targetHash = account == null
                ? DigestUtil.sha256Hex("email:" + rawEmail.strip().toLowerCase(Locale.ROOT))
                : DigestUtil.sha256Hex("account:" + account.accountId());
        boolean deliverable = account != null && available(account);
        return issue(deliverable ? account : null, mask(rawEmail), targetHash);
    }

    /**
     * 为认证尝试已经确定的 Account 签发附加因素 Challenge。
     *
     * @param account 有效认证尝试绑定的 Account
     * @return 不包含完整邮箱的 Challenge
     */
    @Transactional
    @CheckReturnValue
    public @NotNull EmailCodeChallenge issue(@NotNull Account account) {
        Account active = accounts.activeById(Objects.requireNonNull(account, "Account 不能为空").accountId());
        if (!available(active)) {
            throw new BusinessException(EmailCodeErrorCode.CHALLENGE_UNAVAILABLE);
        }
        return issue(active, "***", DigestUtil.sha256Hex("account:" + active.accountId()));
    }

    /** @return 当前 Account 是否存在启用 Email 模板、SMTP 配置和可解析邮箱 */
    @CheckReturnValue
    public boolean available(@NotNull Account account) {
        Account checked = Objects.requireNonNull(account, "Account 不能为空");
        return notifications.isAvailable(EmailCodeNotice.TYPE, NotificationRecipient.account(checked.accountId()));
    }

    /**
     * 单次消费 Challenge 并验证验证码。
     *
     * @param proof Challenge 标识和原始验证码
     * @return 已确认 Account 和消费后版本
     */
    @CheckReturnValue
    public @NotNull ConsumedEmailCodeChallenge consume(@NotNull EmailCodeProof proof) {
        EmailCodeProof checkedProof = Objects.requireNonNull(proof, "Email Code Proof 不能为空");
        EmailCodeChallengeState state = store.find(checkedProof.challengeId());
        String hash = state == null ? dummyHash : state.codeHash();
        boolean matches = hasher.matches(checkedProof.code(), hash);
        if (state == null) {
            throw new BusinessException(EmailCodeErrorCode.CODE_REJECTED);
        }
        Instant now = clock.instant();
        boolean valid = state.status() == PENDING
                && now.isBefore(state.expiresAt())
                && state.accountId() > 0
                && CODE.matcher(checkedProof.code()).matches()
                && matches;
        Version consumed = store.verify(state, valid, settings.MAXIMUM_ATTEMPTS.get());
        if (!valid || consumed == null) {
            throw new BusinessException(EmailCodeErrorCode.CODE_REJECTED);
        }
        try {
            Account account = accounts.activeById(state.accountId());
            return new ConsumedEmailCodeChallenge(state.challengeId(), account, consumed);
        } catch (RuntimeException exception) {
            throw new BusinessException(EmailCodeErrorCode.CODE_REJECTED, exception);
        }
    }

    /** 最终完成认证前复核 Challenge 仍为相同可用账号、消费状态和版本。 */
    public void verifyConsumedChallenge(
            @NotNull EmailCodeChallengeId challengeId,
            long accountId,
            @NotNull Version version) {
        EmailCodeChallengeState state = store.find(challengeId);
        if (state == null
                || state.accountId() != accountId
                || state.status() != CONSUMED
                || !state.version().equals(version)
                || !clock.instant().isBefore(state.expiresAt())) {
            throw new IllegalStateException("Email Code Challenge 已变化或不可用");
        }
        try {
            Account active = accounts.activeById(accountId);
            if (active.accountId() != accountId) {
                throw new IllegalStateException("AccountDirectory 返回了其他 Account");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Email Code Challenge 已变化或不可用", exception);
        }
    }

    /** 创建 Challenge、保存摘要，并仅为真实可投递 Account 可靠登记通知。 */
    private @NotNull EmailCodeChallenge issue(
            @Nullable Account account,
            @NotNull String maskedRecipient,
            @NotNull String targetHash) {
        Duration ttl = settings.CHALLENGE_TTL.get();
        Duration resendInterval = settings.RESEND_INTERVAL.get();
        if (!store.reserveCooldown(targetHash, resendInterval)) {
            throw new BusinessException(EmailCodeErrorCode.RESEND_TOO_SOON);
        }
        Instant now = clock.instant();
        String code = generateCode();
        EmailCodeChallengeState state = new EmailCodeChallengeState(
                new EmailCodeChallengeId(UUID.randomUUID().toString()),
                account == null ? 0 : account.accountId(),
                hasher.hash(code),
                maskedRecipient,
                now.plus(ttl),
                now.plus(resendInterval),
                0,
                PENDING,
                new Version(0));
        store.create(state);

        // 只有真实且当前可投递的 Account 才写入 Outbox；dummy Challenge 保持相同响应结构但不包含投递目标。
        Id messageId = null;
        if (account != null) {
            long minutes = Math.max(1, (ttl.toSeconds() + 59) / 60);
            messageId = notifications.enqueue(
                    EmailCodeNotice.TYPE,
                    new EmailCodeNotice(account.accountId(), code, minutes));
        }
        LOG.debug(
                "Email Code Challenge 已签发 | deliverable={} messageId={} expiresAt={}",
                account != null,
                messageId,
                state.expiresAt());
        return state.publicChallenge();
    }

    /** @return 使用密码学随机源生成的六位验证码 */
    private @NotNull String generateCode() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    /** 把调用方已经知道的邮箱转换为不包含完整地址的提示。 */
    private static @NotNull String mask(@NotNull String email) {
        String value = email.strip();
        int at = value.lastIndexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return "***";
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        String localHint = local.length() == 1 ? "*" : local.charAt(0) + "***";
        int dot = domain.indexOf('.');
        String domainHint = dot <= 0 ? "***" : domain.charAt(0) + "***" + domain.substring(dot);
        return localHint + '@' + domainHint;
    }

}
