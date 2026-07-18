package com.sca4d.iam.recovery.service;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.recovery.IssuedRecoveryCodes;
import com.sca4d.iam.recovery.RecoveryCodeCandidate;
import com.sca4d.iam.recovery.RecoveryCodeService;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import com.sca4d.iam.recovery.RecoveryCodeVerification;
import com.sca4d.iam.recovery.repository.RecoveryCodeRepository;
import com.sca4d.iam.subject.SubjectService;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 使用安全随机数生成恢复码，并只把 Argon2id 摘要交给 Repository 保存。 */
@Service
@RequiredArgsConstructor
public class DefaultRecoveryCodeService implements RecoveryCodeService {

    private static final int CODE_COUNT = 10;
    private static final int CODE_BYTES = 10;
    private static final PasswordEncoder HASHER = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private static final String UNKNOWN_CODE_HASH = HASHER.encode("unknown-account-recovery-code");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private final RecoveryCodeRepository repository;
    private final AccountService accountService;
    private final SubjectService subjectService;

    @Override
    public @NotNull IssuedRecoveryCodes issue(@NotNull Long accountId) {
        List<String> codes = new ArrayList<>(CODE_COUNT);
        List<String> hashes = new ArrayList<>(CODE_COUNT);
        for (int index = 0; index < CODE_COUNT; index++) {
            byte[] randomBytes = new byte[CODE_BYTES];
            RANDOM.nextBytes(randomBytes);
            String compact = HEX.formatHex(randomBytes);
            String code = compact.substring(0, 5)
                    + "-"
                    + compact.substring(5, 10)
                    + "-"
                    + compact.substring(10, 15)
                    + "-"
                    + compact.substring(15, 20);
            codes.add(code);
            // 数据库只接收去掉展示分隔符后的摘要；明文集合只保留到当前 Service 返回值和 HTTP 响应。
            hashes.add(HASHER.encode(code.replace("-", "").toLowerCase(Locale.ROOT)));
        }
        RecoveryCodeSummary summary = repository.replace(accountId, hashes);
        return new IssuedRecoveryCodes(summary, codes);
    }

    @Override
    public void revoke(@NotNull Long accountId) {
        repository.revoke(accountId);
    }

    @Override
    public @NotNull Map<Long, RecoveryCodeSummary> findByAccounts(
            @NotNull Collection<Long> accountIds) {
        return repository.findCurrent(accountIds);
    }

    @Override
    public @NotNull RecoveryCodeVerification consume(
            @NotNull Long accountId, @NotNull String code) {
        String normalized = code.replace("-", "").toLowerCase(Locale.ROOT);
        boolean formatValid = normalized.matches("[0-9a-f]{20}");
        List<RecoveryCodeCandidate> candidates = repository.findUnused(accountId);
        RecoveryCodeCandidate matched = null;
        // 每次固定执行十次 Argon2id 校验；未知账号、剩余数量和命中位置不能通过响应耗时区分。
        for (int index = 0; index < CODE_COUNT; index++) {
            RecoveryCodeCandidate candidate = index < candidates.size() ? candidates.get(index) : null;
            String hash = candidate == null ? UNKNOWN_CODE_HASH : candidate.getCodeHash();
            if (HASHER.matches(normalized, hash) && candidate != null) {
                matched = candidate;
            }
        }
        if (!formatValid || matched == null || !repository.use(matched)) {
            throw new UnauthenticatedException(AuthenticationErrorCode.RECOVERY_CODE_INVALID);
        }
        Account account = requireRecoverableAccount(matched.getAccountId());
        Subject subject = requireActiveSubject(account.getSubjectId());
        return new RecoveryCodeVerification(
                subject, account, matched.getCodeId(), matched.getSetVersion());
    }

    @Override
    public @NotNull RecoveryCodeVerification requireCurrent(
            @NotNull RecoveryCodeVerification verification) {
        Account account = requireRecoverableAccount(verification.getAccount().getAccountId());
        Subject subject = requireActiveSubject(account.getSubjectId());
        if (!subject.getSubjectId().equals(verification.getSubject().getSubjectId())
                || !repository.isCurrentUsed(
                        account.getAccountId(),
                        verification.getCodeId(),
                        verification.getSetVersion())) {
            throw new UnauthenticatedException();
        }
        return new RecoveryCodeVerification(
                subject,
                account,
                verification.getCodeId(),
                verification.getSetVersion());
    }

    /**
     * 读取当前仍允许恢复且要求第二因子的 Account。
     *
     * @param accountId 恢复码所属 Account
     * @return 不包含 Credential 和恢复码数据的认证运行时 Account
     * @throws UnauthenticatedException Account 停用或已关闭第二因子时抛出
     */
    private @NotNull Account requireRecoverableAccount(@NotNull Long accountId) {
        IamAccountEntity stored;
        try {
            stored = accountService.require(accountId);
        } catch (BizException exception) {
            throw new UnauthenticatedException();
        }
        if (stored.getStatus() != AccountStatus.ACTIVE || !Boolean.TRUE.equals(stored.getSecondFactorRequired())) {
            throw new UnauthenticatedException();
        }
        return new Account(
                stored.getId(),
                stored.getSubjectId(),
                stored.getScopeType(),
                stored.getScopeId(),
                true);
    }

    /**
     * 读取恢复码 Account 所属且当前仍允许认证的 Subject。
     *
     * @param subjectId Account 保存的 Subject 主键
     * @return 不包含资料聚合的认证运行时 Subject
     * @throws UnauthenticatedException Subject 已停用时抛出
     */
    private @NotNull Subject requireActiveSubject(@NotNull Long subjectId) {
        IamSubjectEntity stored;
        try {
            stored = subjectService.require(subjectId);
        } catch (BizException exception) {
            throw new UnauthenticatedException();
        }
        if (stored.getStatus() != SubjectStatus.ACTIVE) {
            throw new UnauthenticatedException();
        }
        return new Subject(stored.getId(), stored.getDisplayName());
    }
}
