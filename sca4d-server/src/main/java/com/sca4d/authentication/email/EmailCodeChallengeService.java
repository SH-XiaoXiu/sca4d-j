package com.sca4d.authentication.email;

import com.sca4d.authentication.AuthenticationProcess;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** 为当前认证投递、消费和复核邮件验证码 Challenge。 */
public interface EmailCodeChallengeService {

    /**
     * 为已经确定 Subject 与 Account 的认证投递一次邮件验证码。
     *
     * @param authentication 当前正在等待邮件验证码的认证
     * @param locale 邮件模板语言
     * @return Challenge 标识、脱敏收件人和时间限制
     */
    @NotNull
    EmailCodeChallengeDelivery issue(
            @NotNull AuthenticationProcess authentication, @NotNull Locale locale);

    /**
     * 校验并原子消费指定认证的邮件验证码 Challenge。
     *
     * @param authenticationId Challenge 所属认证 UUID
     * @param challengeId Challenge UUID
     * @param code 当前请求提交的一次性验证码
     * @return 已消费且仍绑定原 Subject、Account 和 Contact 的 Challenge
     */
    @NotNull
    EmailCodeChallenge consume(
            @NotNull String authenticationId, @NotNull String challengeId, @NotNull String code);

    /**
     * 在认证完成前复核已消费 Challenge 和 Contact 验证版本。
     *
     * @param authenticationId Challenge 所属认证 UUID
     * @param challengeId Challenge UUID
     * @param version Flowable 保存的 Challenge 消费版本
     * @return 仍然有效的已消费 Challenge
     */
    @NotNull
    EmailCodeChallenge requireConsumed(
            @NotNull String authenticationId, @NotNull String challengeId, long version);
}
