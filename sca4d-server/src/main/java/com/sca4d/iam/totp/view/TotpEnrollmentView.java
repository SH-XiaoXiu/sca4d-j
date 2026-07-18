package com.sca4d.iam.totp.view;

import com.sca4d.iam.totp.TotpEnrollment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 不包含密钥、二维码和浏览器摘要的短期 TOTP enrollment 响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TotpEnrollmentView {
    private String enrollmentId;
    private Instant expiresAt;

    /** @return enrollment 的安全响应表示 */
    public static @NotNull TotpEnrollmentView from(@NotNull TotpEnrollment enrollment) {
        return new TotpEnrollmentView(enrollment.getEnrollmentId(), enrollment.getExpiresAt());
    }
}
