package com.sca4d.authentication.email.service;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.email.EmailCodeChallenge;
import com.sca4d.authentication.email.EmailCodeChallengeDelivery;
import com.sca4d.authentication.email.EmailCodeChallengeService;
import com.sca4d.authentication.email.EmailCodeChallengeState;
import com.sca4d.authentication.email.store.EmailCodeChallengeStore;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.SensitiveUtils;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.mail.MailService;
import com.sca4d.mail.MailTemplateService;
import com.sca4d.mail.RenderedMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Redis 邮件验证码的签发、投递、有限次验证和单次消费实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEmailCodeChallengeService implements EmailCodeChallengeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PasswordEncoder CODES = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private static final String UNKNOWN_CODE_HASH = CODES.encode("000000");

    private final EmailCodeChallengeStore store;
    private final ContactService contactService;
    private final MailTemplateService templateService;
    private final MailService mailService;
    private final Sca4djAuthenticationProperties properties;

    @Override
    public @NotNull EmailCodeChallengeDelivery issue(
            @NotNull AuthenticationProcess authentication, @NotNull Locale locale) {
        Sca4djAuthenticationProperties.EmailCode configuration = properties.getEmailCode();
        if (!configuration.isEnabled()) {
            throw new BizException(AuthenticationErrorCode.SECOND_FACTOR_UNAVAILABLE);
        }
        IamContactEntity email = contactService.current(authentication.getSubjectId(), ContactType.EMAIL);
        if (email == null) {
            throw new BizException(IamErrorCode.ACCOUNT_EMAIL_REQUIRED);
        }
        Instant now = Instant.now();
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        EmailCodeChallenge challenge = new EmailCodeChallenge()
                .setChallengeId(UUID.randomUUID().toString())
                .setAuthenticationId(authentication.getAuthenticationId())
                .setSubjectId(authentication.getSubjectId())
                .setAccountId(authentication.getAccountId())
                .setScopeType(authentication.getScopeType())
                .setScopeId(authentication.getScopeId())
                .setContactId(email.getId())
                .setContactVersion(email.getVersion())
                .setVerifiedContactVersion(email.getVerifiedAt() == null
                        ? email.getVersion() + 1
                        : email.getVersion())
                .setCodeHash(CODES.encode(code))
                .setState(EmailCodeChallengeState.PENDING)
                .setVersion(0L)
                .setAttemptsRemaining(configuration.getMaxAttempts())
                .setExpiresAt(now.plus(configuration.getTtl()));
        if (!store.create(challenge, configuration.getTtl(), configuration.getResendInterval())) {
            throw new RateLimitException(
                    AuthenticationErrorCode.EMAIL_CODE_RESEND_LATER,
                    Math.max(1, configuration.getResendInterval().toSeconds()));
        }
        try {
            long minutes = Math.max(1, (configuration.getTtl().toSeconds() + 59) / 60);
            RenderedMail mail = templateService.render(
                    configuration.getTemplateFamily(),
                    locale.toLanguageTag(),
                    Map.of(
                            "code", code,
                            "displayName", "",
                            "expiresInMinutes", Long.toString(minutes)));
            mailService.send(
                    configuration.getSenderId(),
                    email.getValue(),
                    mail.getSubject(),
                    mail.getText(),
                    mail.getHtml());
        } catch (RuntimeException exception) {
            store.cancel(authentication.getAuthenticationId(), challenge.getChallengeId());
            log.warn(
                    "邮件验证码发送失败，当前验证已取消 | challengeId={} authenticationId={} subjectId={} senderId={} failureType={}",
                    challenge.getChallengeId(),
                    authentication.getAuthenticationId(),
                    authentication.getSubjectId(),
                    configuration.getSenderId(),
                    exception.getClass().getName());
            throw exception;
        }
        log.info(
                "邮件验证码已发送 | challengeId={} authenticationId={} subjectId={} senderId={}",
                challenge.getChallengeId(),
                authentication.getAuthenticationId(),
                authentication.getSubjectId(),
                configuration.getSenderId());
        return new EmailCodeChallengeDelivery(
                challenge.getChallengeId(),
                SensitiveUtils.email(email.getValue()),
                challenge.getExpiresAt(),
                now.plus(configuration.getResendInterval()));
    }

    @Override
    public @NotNull EmailCodeChallenge consume(
            @NotNull String authenticationId, @NotNull String challengeId, @NotNull String code) {
        EmailCodeChallenge challenge = store.find(authenticationId, challengeId);
        if (challenge == null) {
            CODES.matches(code, UNKNOWN_CODE_HASH);
            throw new BizException(AuthenticationErrorCode.EMAIL_CODE_CHALLENGE_UNAVAILABLE);
        }
        if (challenge.getState() != EmailCodeChallengeState.PENDING
                || challenge.getExpiresAt().isBefore(Instant.now())
                || challenge.getAttemptsRemaining() < 1) {
            CODES.matches(code, challenge.getCodeHash());
            throw new BizException(AuthenticationErrorCode.EMAIL_CODE_INVALID);
        }
        long expectedVersion = challenge.getVersion();
        if (!CODES.matches(code, challenge.getCodeHash())) {
            int remaining = challenge.getAttemptsRemaining() - 1;
            challenge.setAttemptsRemaining(remaining)
                    .setState(remaining == 0
                            ? EmailCodeChallengeState.EXHAUSTED
                            : EmailCodeChallengeState.PENDING)
                    .setVersion(expectedVersion + 1);
            if (!store.advance(
                    challenge,
                    expectedVersion,
                    EmailCodeChallengeState.PENDING,
                    remaining == 0)) {
                throw new BizException(AuthenticationErrorCode.EMAIL_CODE_CHALLENGE_CHANGED);
            }
            log.debug(
                    "邮件验证码校验未通过 | challengeId={} authenticationId={} attemptsRemaining={}",
                    challengeId,
                    authenticationId,
                    remaining);
            throw new BizException(AuthenticationErrorCode.EMAIL_CODE_INVALID);
        }
        IamContactEntity verified = contactService.verify(
                challenge.getSubjectId(),
                challenge.getContactId(),
                ContactType.EMAIL,
                challenge.getContactVersion());
        if (!challenge.getVerifiedContactVersion().equals(verified.getVersion())) {
            throw new BizException(IamErrorCode.CONTACT_CHANGED);
        }
        challenge.setState(EmailCodeChallengeState.CONSUMED).setVersion(expectedVersion + 1);
        if (!store.advance(
                challenge, expectedVersion, EmailCodeChallengeState.PENDING, true)) {
            throw new BizException(AuthenticationErrorCode.EMAIL_CODE_CHALLENGE_CHANGED);
        }
        log.info(
                "邮件验证码已验证并消费 | challengeId={} authenticationId={} subjectId={} accountId={}",
                challengeId,
                authenticationId,
                challenge.getSubjectId(),
                challenge.getAccountId());
        return challenge;
    }

    @Override
    public @NotNull EmailCodeChallenge requireConsumed(
            @NotNull String authenticationId, @NotNull String challengeId, long version) {
        EmailCodeChallenge challenge = store.find(authenticationId, challengeId);
        if (challenge == null
                || challenge.getState() != EmailCodeChallengeState.CONSUMED
                || !Long.valueOf(version).equals(challenge.getVersion())
                || challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(AuthenticationErrorCode.EMAIL_CODE_CHALLENGE_UNAVAILABLE);
        }
        contactService.requireVerified(
                challenge.getSubjectId(),
                challenge.getContactId(),
                ContactType.EMAIL,
                challenge.getVerifiedContactVersion());
        return challenge;
    }
}
