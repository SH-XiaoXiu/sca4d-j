package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.authentication.password.audit.PasswordLoginAuditPayload;
import com.sca4d.iam.error.KnownCredentialRejectedException;
import org.junit.jupiter.api.Test;

/** 验证密码登录审计内容不会保存密码并能生成目标摘要。 */
class PasswordLoginAuditPayloadTest {

    @Test
    void shouldCaptureSuccessfulSubjectWithoutRawAccount() {
        PasswordLoginAuditPayload payload = PasswordLoginAuditPayload.capture(
                "administrator", "browser", "Chrome", 10L, null);

        assertThat(payload.getSubjectId()).isEqualTo(10L);
        assertThat(payload.getCredentialId()).isNull();
        assertThat(payload.getLoginAccountMask()).isEqualTo("a***r");
        assertThat(payload.toString()).doesNotContain("administrator");
    }

    @Test
    void shouldCaptureKnownCredentialFailureWithoutRawAccount() {
        PasswordLoginAuditPayload payload = PasswordLoginAuditPayload.capture(
                "administrator", null, "Chrome", null, new KnownCredentialRejectedException(20L, 10L));

        assertThat(payload.getCredentialId()).isEqualTo(20L);
        assertThat(payload.getSubjectId()).isEqualTo(10L);
        assertThat(payload.getLoginAccountMask()).isEqualTo("a***r");
        assertThat(payload.toString()).doesNotContain("administrator");
    }
}
