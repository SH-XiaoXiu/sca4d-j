package com.sca4d.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.iam.passkey.PasskeyCredential;
import com.sca4d.iam.passkey.PasskeyCredentialScheme;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserVerificationRequirement;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证 Passkey Scheme 的 discoverable credential、UV 和专属子表写入要求。 */
class PasskeyCredentialSchemeTest {

    @Test
    void shouldRequireResidentKeyAndUserVerificationForRegistration() {
        RelyingParty relyingParty = mock(RelyingParty.class);
        PublicKeyCredentialCreationOptions request = mock(PublicKeyCredentialCreationOptions.class);
        when(relyingParty.startRegistration(any())).thenReturn(request);
        PasskeyCredentialScheme scheme = scheme(
                mock(PasskeyRepository.class), relyingParty, mock(IdGenerator.class));

        assertThat(scheme.registrationOptions(new byte[32], "管理员", 60_000L))
                .isSameAs(request);

        ArgumentCaptor<StartRegistrationOptions> options = ArgumentCaptor.forClass(
                StartRegistrationOptions.class);
        verify(relyingParty).startRegistration(options.capture());
        assertThat(options.getValue().getAuthenticatorSelection())
                .isPresent()
                .get()
                .satisfies(selection -> {
                    assertThat(selection.getResidentKey()).contains(ResidentKeyRequirement.REQUIRED);
                    assertThat(selection.getUserVerification())
                            .contains(UserVerificationRequirement.REQUIRED);
                });
        assertThat(options.getValue().getUser().getId().size()).isEqualTo(32);
    }

    @Test
    void shouldRequireUserVerificationAndBindAccountForAssertion() {
        RelyingParty relyingParty = mock(RelyingParty.class);
        AssertionRequest request = mock(AssertionRequest.class);
        when(relyingParty.startAssertion(any())).thenReturn(request);
        PasskeyCredentialScheme scheme = scheme(
                mock(PasskeyRepository.class), relyingParty, mock(IdGenerator.class));
        byte[] userHandle = new byte[32];

        assertThat(scheme.assertionRequest(userHandle, 60_000L)).isSameAs(request);

        ArgumentCaptor<StartAssertionOptions> options = ArgumentCaptor.forClass(
                StartAssertionOptions.class);
        verify(relyingParty).startAssertion(options.capture());
        assertThat(options.getValue().getUserVerification())
                .contains(UserVerificationRequirement.REQUIRED);
        assertThat(options.getValue().getUserHandle())
                .isPresent()
                .get()
                .extracting(value -> value.getBytes())
                .isEqualTo(userHandle);
    }

    @Test
    void shouldBindPublicKeyAndAuthenticatorStateToDedicatedRepository() {
        PasskeyRepository repository = mock(PasskeyRepository.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(ids.nextId()).thenReturn(40L);
        PasskeyCredential credential = new PasskeyCredential(
                new byte[] {0},
                new byte[] {1},
                new byte[] {2},
                3L,
                new byte[16],
                true,
                false,
                "工作电脑");
        PasskeyCredentialScheme scheme = scheme(repository, mock(RelyingParty.class), ids);

        assertThat(scheme.credentialType()).isEqualTo(CredentialType.PASSKEY);
        assertThat(scheme.bind(30L, credential)).isEqualTo(40L);
        verify(repository).save(
                40L,
                30L,
                credential.getUserHandle(),
                credential.getPasskeyCredentialId(),
                credential.getPublicKeyCose(),
                3L,
                credential.getAaguid(),
                true,
                false,
                "工作电脑");
    }

    /** @return 使用指定依赖的 Passkey CredentialScheme */
    private PasskeyCredentialScheme scheme(
            PasskeyRepository repository, RelyingParty relyingParty, IdGenerator ids) {
        return new PasskeyCredentialScheme(repository, relyingParty, ids);
    }
}
