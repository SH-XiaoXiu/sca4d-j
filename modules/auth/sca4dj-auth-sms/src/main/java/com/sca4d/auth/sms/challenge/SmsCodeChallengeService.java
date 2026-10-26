package com.sca4d.auth.sms.challenge;

import static com.sca4d.auth.sms.challenge.SmsCodeChallengeStatus.CONSUMED;
import static com.sca4d.auth.sms.challenge.SmsCodeChallengeStatus.PENDING;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.auth.sms.SmsCodeChallenge;
import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.auth.sms.SmsCodeErrorCode;
import com.sca4d.auth.sms.SmsCodeNotice;
import com.sca4d.auth.sms.SmsCodeProof;
import com.sca4d.auth.sms.config.SmsCodeSettings;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.security.SecretHasher;
import org.jetbrains.annotations.NotNull;
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

/** 签发匿名一致或已知 Account 的 SMS Challenge，并原子消费验证码。 */
public class SmsCodeChallengeService {
    private static final Logger LOG = LoggerFactory.getLogger(SmsCodeChallengeService.class);

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private final AccountDirectory accounts;

    private final Clock clock;

    private final Notifications notifications;

    private final RealmKey accountRealm;

    private final RedisSmsCodeChallengeStore store;

    private final SecretHasher hasher;

    private final SecureRandom random;

    private final SmsCodeSettings settings;

    private final String dummyHash;

    /** 创建 SMS Challenge 服务。 */
    public SmsCodeChallengeService(
            @NotNull AccountDirectory accounts,
            @NotNull RealmKey accountRealm,
            @NotNull Notifications notifications,
            @NotNull RedisSmsCodeChallengeStore store,
            @NotNull SecretHasher hasher,
            @NotNull SmsCodeSettings settings,
            @NotNull Clock clock) {
        this(accounts, accountRealm, notifications, store, hasher, settings, clock, new SecureRandom());
    }

    /** 使用可替换随机源创建测试可控的 SMS Challenge 服务。 */
    SmsCodeChallengeService(
            AccountDirectory accounts,
            RealmKey accountRealm,
            Notifications notifications,
            RedisSmsCodeChallengeStore store,
            SecretHasher hasher,
            SmsCodeSettings settings,
            Clock clock,
            SecureRandom random) {
        this.accounts = Objects.requireNonNull(accounts, "AccountDirectory 不能为空");
        this.accountRealm = Objects.requireNonNull(accountRealm, "Account Realm 不能为空");
        this.notifications = Objects.requireNonNull(notifications, "Notifications 不能为空");
        this.store = Objects.requireNonNull(store, "SMS Challenge Store 不能为空");
        this.hasher = Objects.requireNonNull(hasher, "SecretHasher 不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
        this.random = Objects.requireNonNull(random, "SecureRandom 不能为空");
        this.dummyHash = hasher.hash(generateCode());
    }

    /** 使用 PHONE Identifier 签发匿名一致的登录 Challenge。 */
    @Transactional
    public @NotNull SmsCodeChallenge issue(@NotNull String phoneNumber) {
        String raw = Objects.requireNonNull(phoneNumber, "PHONE 登录标识不能为空");
        Account account = accounts.resolve(accountRealm, SmsCodeAuthenticationMethod.PHONE_IDENTIFIER_TYPE, raw)
                .orElse(null);
        String targetHash = account == null
                ? DigestUtil.sha256Hex("phone:" + raw)
                : DigestUtil.sha256Hex("account:" + account.accountId());
        return issue(account != null && available(account) ? account : null, mask(raw), targetHash);
    }

    /**
     * 为认证尝试已经确定的 Account 签发 Challenge。
     *
     * <p>方法重新读取启用的 Account，并要求它具备可用 SMS 渠道和已验证手机号。成功时在 Redis 中保存 PENDING
     * Challenge，并在当前事务中把短信验证码通知写入 Outbox；返回值不包含验证码和完整手机号。
     * 副作用：先在 Redis 登记重发冷却，再保存 Challenge。Redis 写入直接执行，不随外层事务回滚撤销。
     *
     * @param account 有效认证尝试绑定的 Account
     * @return 不包含完整手机号的 Challenge
     * @throws BusinessException Account 不存在或已停用时抛出当前 IAM 实现的 ACCOUNT_UNAVAILABLE；
     *                           SMS 渠道或手机号不可用时抛出 {@link SmsCodeErrorCode#CHALLENGE_UNAVAILABLE}；
     *                           重发间隔内抛出 {@link SmsCodeErrorCode#RESEND_TOO_SOON}；
     *                           Challenge 标识冲突或有效期不为正数时抛出对应的 Challenge 错误码
     */
    @Transactional
    public @NotNull SmsCodeChallenge issue(@NotNull Account account) {
        Account active = accounts.activeById(Objects.requireNonNull(account, "Account 不能为空").accountId());
        if (!available(active))
            throw new BusinessException(SmsCodeErrorCode.CHALLENGE_UNAVAILABLE);
        return issue(active, "***", DigestUtil.sha256Hex("account:" + active.accountId()));
    }

    /** @return 当前 Account 是否存在可用 SMS 渠道和已验证手机号 */
    public boolean available(@NotNull Account account) {
        return notifications.isAvailable(
                SmsCodeNotice.TYPE,
                NotificationRecipient.account(Objects.requireNonNull(account, "Account 不能为空").accountId()));
    }

    /**
     * 单次消费 Challenge 并确认 Account。
     *
     * <p>Challenge 不存在时仍执行一次摘要比较，避免通过响应时间判断 Challenge 是否存在。只有 Challenge 为 PENDING、
     * 未过期、绑定真实 Account、验证码为六位数字且摘要匹配时才成功。
     * 副作用：Challenge 为 PENDING 且未过期时，Redis 脚本都会写入一次结果，并递增版本。验证码不匹配或格式错误
     * 计为一次错误尝试，达到上限后状态变为 EXHAUSTED；成功时状态变为 CONSUMED。Challenge 已消费、已耗尽、
     * 已过期或版本已被其他请求改变时不写入 Redis。成功写入后，若 Account 已停用或不存在，Challenge 保持
     * CONSUMED，方法仍抛出 CODE_REJECTED，不会恢复 Challenge。
     *
     * @param proof Challenge 标识和原始验证码
     * @return 已确认 Account 和消费后版本
     * @throws BusinessException 验证码不可接受、Challenge 不可用，或成功消费后 Account 不可用时抛出
     *                           {@link SmsCodeErrorCode#CODE_REJECTED}
     */
    public @NotNull ConsumedSmsCodeChallenge consume(@NotNull SmsCodeProof proof) {
        SmsCodeProof checked = Objects.requireNonNull(proof, "SMS Code Proof 不能为空");

        // 无论 Challenge 是否存在都执行一次摘要校验，避免通过响应时间判断号码或 Challenge 是否有效。
        SmsCodeChallengeState state = store.find(checked.challengeId());
        String hash = state == null ? dummyHash : state.codeHash();
        boolean matches = hasher.matches(checked.code(), hash);
        if (state == null)
            throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED);
        Instant now = clock.instant();
        boolean valid = state.status() == PENDING
                && now.isBefore(state.expiresAt())
                && state.accountId() > 0
                && CODE.matcher(checked.code()).matches()
                && matches;
        Version consumed = store.verify(state, valid, settings.MAXIMUM_ATTEMPTS.get());
        if (!valid || consumed == null)
            throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED);

        // Redis 已原子消费验证码后，再复核 Account 仍然有效并返回认证所需的引用和版本。
        try {
            return new ConsumedSmsCodeChallenge(state.challengeId(), accounts.activeById(state.accountId()), consumed);
        } catch (BusinessException exception) {
            throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED, exception);
        }
    }

    /**
     * 最终完成认证前复核 Challenge 仍属于相同可用 Account 和消费版本。
     *
     * <p>方法只读取 Redis Challenge 和 Account，不修改任何状态。Challenge 必须仍存在、为 CONSUMED、未过期，
     * 版本等于 {@code version}，并且绑定的 Account 等于 {@code accountId}；之后 Account 仍须启用。
     *
     * @param challengeId 已经消费的 Challenge 标识
     * @param accountId 消费时确认的 Account 标识
     * @param version {@link #consume(SmsCodeProof)} 返回的消费后版本
     * @throws BusinessException Challenge 缺失、已过期、状态或版本不一致、Account 不一致或 Account 已不可用时抛出
     *                           {@link SmsCodeErrorCode#CHALLENGE_CHANGED}
     * @throws IllegalStateException {@code AccountDirectory} 返回的 Account 标识与 {@code accountId} 不一致时抛出
     */
    public void verifyConsumed(@NotNull SmsCodeChallengeId challengeId, long accountId, @NotNull Version version) {
        SmsCodeChallengeState state = store.find(challengeId);
        if (state == null
                || state.accountId() != accountId
                || state.status() != CONSUMED
                || !state.version().equals(version)
                || !clock.instant().isBefore(state.expiresAt())) {
            throw new BusinessException(SmsCodeErrorCode.CHALLENGE_CHANGED);
        }
        try {
            Account active = accounts.activeById(accountId);
            if (active.accountId() != accountId) {
                throw new IllegalStateException("AccountDirectory 返回了其他 Account");
            }
        } catch (BusinessException exception) {
            throw new BusinessException(SmsCodeErrorCode.CHALLENGE_CHANGED, exception);
        }
    }

    /**
     * 登记重发冷却、创建 Challenge 并保存验证码摘要；只为真实可投递的 Account 在当前事务中登记短信通知。
     */
    private SmsCodeChallenge issue(Account account, String maskedRecipient, String targetHash) {
        Duration ttl = settings.CHALLENGE_TTL.get();
        Duration resend = settings.RESEND_INTERVAL.get();
        if (!store.reserveCooldown(targetHash, resend))
            throw new BusinessException(SmsCodeErrorCode.RESEND_TOO_SOON);
        Instant now = clock.instant();
        String code = generateCode();
        SmsCodeChallengeState state = new SmsCodeChallengeState(
                new SmsCodeChallengeId(UUID.randomUUID().toString()),
                account == null ? 0 : account.accountId(),
                hasher.hash(code),
                maskedRecipient,
                now.plus(ttl),
                now.plus(resend),
                0,
                PENDING,
                new Version(0));
        store.create(state);
        Id messageId = null;
        if (account != null) {
            long minutes = Math.max(1, (ttl.toSeconds() + 59) / 60);
            messageId = notifications.enqueue(
                    SmsCodeNotice.TYPE,
                    new SmsCodeNotice(account.accountId(), code, minutes));
        }
        LOG.debug(
                "SMS Code Challenge 已签发 | deliverable={} messageId={} expiresAt={}",
                account != null,
                messageId,
                state.expiresAt());
        return state.publicChallenge();
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    private static String mask(String value) {
        String checked = value.strip();
        return checked.length() <= 7
                ? "***"
                : checked.substring(0, 4) + "***" + checked.substring(checked.length() - 4);
    }

}
