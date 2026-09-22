package com.sca4d.auth.sms.challenge;

import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.auth.sms.SmsCodeChallenge;
import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.auth.sms.SmsCodeErrorCode;
import com.sca4d.auth.sms.SmsCodeNotice;
import com.sca4d.auth.sms.SmsCodeProof;
import com.sca4d.auth.sms.config.SmsCodeSettings;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.security.SecretHasher;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 签发匿名一致或已知 Account 的 SMS Challenge，并原子消费验证码。 */
public class SmsCodeChallengeService {
    private static final Logger LOG = LoggerFactory.getLogger(SmsCodeChallengeService.class);

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private final AccountDirectory accounts;

    private final RealmKey accountRealm;

    private final Notifications notifications;

    private final RedisSmsCodeChallengeStore store;

    private final SecretHasher hasher;

    private final Settings settings;

    private final Clock clock;

    private final SecureRandom random;

    private final String dummyHash;

    /** 创建 SMS Challenge 服务。 */
    public SmsCodeChallengeService(
            @NotNull AccountDirectory accounts,
            @NotNull RealmKey accountRealm,
            @NotNull Notifications notifications,
            @NotNull RedisSmsCodeChallengeStore store,
            @NotNull SecretHasher hasher,
            @NotNull Settings settings,
            @NotNull Clock clock) {
        this(accounts, accountRealm, notifications, store, hasher, settings, clock, new SecureRandom());
    }

    SmsCodeChallengeService(
            AccountDirectory accounts,
            RealmKey accountRealm,
            Notifications notifications,
            RedisSmsCodeChallengeStore store,
            SecretHasher hasher,
            Settings settings,
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
        String targetHash = account == null ? targetHash("phone:" + raw) : targetHash("account:" + account.accountId());
        return issue(account != null && available(account) ? account : null, mask(raw), targetHash);
    }

    /** 为认证尝试已经确定的 Account 签发 Challenge。 */
    @Transactional
    public @NotNull SmsCodeChallenge issue(@NotNull Account account) {
        Account active = accounts.activeById(Objects.requireNonNull(account, "Account 不能为空").accountId());
        if (!available(active))
            throw new BusinessException(SmsCodeErrorCode.CHALLENGE_UNAVAILABLE);
        return issue(active, "***", targetHash("account:" + active.accountId()));
    }

    /** @return 当前 Account 是否存在可用 SMS 渠道和已验证手机号 */
    public boolean available(@NotNull Account account) {
        return notifications.isAvailable(
                SmsCodeNotice.TYPE,
                NotificationRecipient.account(Objects.requireNonNull(account, "Account 不能为空").accountId()));
    }

    /** 单次消费 Challenge 并确认 Account。 */
    public @NotNull ConsumedSmsCodeChallenge consume(@NotNull SmsCodeProof proof) {
        SmsCodeProof checked = Objects.requireNonNull(proof, "SMS Code Proof 不能为空");

        // 无论 Challenge 是否存在都执行一次摘要校验，避免通过响应时间判断号码或 Challenge 是否有效。
        SmsCodeChallengeState state = store.find(checked.challengeId());
        String hash = state == null ? dummyHash : state.codeHash();
        boolean matches = hasher.matches(checked.code(), hash);
        if (state == null)
            throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED);
        Instant now = clock.instant();
        boolean valid = state.status() == SmsCodeChallengeStatus.PENDING
                && now.isBefore(state.expiresAt())
                && state.accountId() > 0
                && CODE.matcher(checked.code()).matches()
                && matches;
        Version consumed = store.verify(state, valid, settings.get(SmsCodeSettings.MAXIMUM_ATTEMPTS));
        if (!valid || consumed == null)
            throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED);

        // Redis 已原子消费验证码后，再复核 Account 仍然有效并返回认证所需的引用和版本。
        try {
            return new ConsumedSmsCodeChallenge(state.challengeId(), accounts.activeById(state.accountId()), consumed);
        } catch (RuntimeException exception) {
            throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED, exception);
        }
    }

    /** 最终完成认证前复核 Challenge 仍属于相同可用 Account 和消费版本。 */
    public void verifyConsumed(@NotNull SmsCodeChallengeId challengeId, long accountId, @NotNull Version version) {
        SmsCodeChallengeState state = store.find(challengeId);
        if (state == null
                || state.accountId() != accountId
                || state.status() != SmsCodeChallengeStatus.CONSUMED
                || !state.version().equals(version)
                || !clock.instant().isBefore(state.expiresAt())) {
            throw new IllegalStateException("SMS Challenge 已变化或不可用");
        }
        try {
            Account active = accounts.activeById(accountId);
            if (active.accountId() != accountId) {
                throw new IllegalStateException("AccountDirectory 返回了其他 Account");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("SMS Challenge 已变化或不可用", exception);
        }
    }

    private SmsCodeChallenge issue(Account account, String maskedRecipient, String targetHash) {
        Duration ttl = settings.get(SmsCodeSettings.CHALLENGE_TTL);
        Duration resend = settings.get(SmsCodeSettings.RESEND_INTERVAL);
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
                SmsCodeChallengeStatus.PENDING,
                new Version(0));
        store.create(state);
        MessageId messageId = null;
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

    private static String targetHash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
