package com.sca4d.authentication.email.view;

import com.sca4d.authentication.email.EmailCodeChallengeDelivery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 不包含验证码和完整收件地址的邮件验证码 Challenge 响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailCodeChallengeView {

    private String challengeId;
    private String maskedRecipient;
    private Instant expiresAt;
    private Instant resendAt;

    /**
     * 创建可返回给浏览器的 Challenge 视图。
     *
     * @param delivery 邮件服务已经接受的投递结果
     * @return Challenge 标识、脱敏收件人和时间限制
     */
    public static @NotNull EmailCodeChallengeView from(
            @NotNull EmailCodeChallengeDelivery delivery) {
        return new EmailCodeChallengeView(
                delivery.getChallengeId(),
                delivery.getMaskedRecipient(),
                delivery.getExpiresAt(),
                delivery.getResendAt());
    }
}
