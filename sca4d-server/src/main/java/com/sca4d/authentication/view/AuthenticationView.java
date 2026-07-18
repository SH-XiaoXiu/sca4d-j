package com.sca4d.authentication.view;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 当前 Authentication 等待的输入及已经确定的账号。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationView {
    private String authenticationId;
    private AuthenticationPurpose purpose;
    private String activityId;
    private Long subjectId;
    private Long accountId;
    private Instant expiresAt;

    public static @NotNull AuthenticationView from(@NotNull AuthenticationProcess authentication) {
        return new AuthenticationView(
                authentication.getAuthenticationId(),
                authentication.getPurpose(),
                authentication.getActivityId(),
                authentication.getSubjectId(),
                authentication.getAccountId(),
                authentication.getExpiresAt());
    }
}
