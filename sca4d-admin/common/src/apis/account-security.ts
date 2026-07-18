import type { ApiClient } from "../api-client";
import type {
  AccountSecondFactorRequest,
  AccountSecondFactorView,
  AccountSecurityView,
  AccountSessionView,
  AvailableIdentityProvider,
  ExternalIdentityView,
  Id,
  PasskeyCredentialChangeView,
  PasskeyCredentialCreateRequest,
  PasskeyRegistrationCreateRequest,
  PasskeyRegistrationView,
  TotpCredentialChangeView,
  TotpCredentialCreateRequest,
  TotpCredentialReplaceRequest,
  TotpEnrollmentView
} from "../types";

/** 创建当前用户账号、认证资料和登录会话的请求方法。 */
export function createAccountSecurityApi(client: ApiClient) {
  const root = "/me/accounts";
  return {
    accounts(): Promise<AccountSecurityView[]> {
      return client.get<AccountSecurityView[]>(root);
    },
    sessions(accountId: Id): Promise<AccountSessionView[]> {
      return client.get<AccountSessionView[]>(`${root}/${encodeURIComponent(accountId)}/sessions`);
    },
    externalIdentities(accountId: Id): Promise<ExternalIdentityView[]> {
      return client.get<ExternalIdentityView[]>(
        `${root}/${encodeURIComponent(accountId)}/external-identities`
      );
    },
    availableIdentityProviders(accountId: Id): Promise<AvailableIdentityProvider[]> {
      return client.get<AvailableIdentityProvider[]>(
        `${root}/${encodeURIComponent(accountId)}/external-identities/providers`
      );
    },
    deleteExternalIdentity(
      accountId: Id,
      identityId: Id,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/external-identities/${encodeURIComponent(identityId)}`,
        method: "DELETE",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    },
    changeSecondFactorRequirement(
      accountId: Id,
      request: AccountSecondFactorRequest,
      authenticationId: string
    ): Promise<AccountSecondFactorView> {
      return client.request<AccountSecondFactorView, AccountSecondFactorRequest>({
        path: `${root}/${encodeURIComponent(accountId)}/second-factor/requirement`,
        method: "PUT",
        headers: { "X-Authentication-Id": authenticationId },
        body: request
      });
    },
    createTotpEnrollment(accountId: Id, authenticationId: string): Promise<TotpEnrollmentView> {
      return client.request<TotpEnrollmentView>({
        path: `${root}/${encodeURIComponent(accountId)}/totp/enrollments`,
        method: "POST",
        headers: { "X-Authentication-Id": authenticationId }
      });
    },
    totpEnrollmentQrCode(accountId: Id, enrollmentId: string): Promise<Blob> {
      return client.getBlob(
        `${root}/${encodeURIComponent(accountId)}/totp/enrollments/${encodeURIComponent(enrollmentId)}/qr-code`
      );
    },
    activateTotpCredential(
      accountId: Id,
      request: TotpCredentialCreateRequest
    ): Promise<TotpCredentialChangeView> {
      return client.request<TotpCredentialChangeView, TotpCredentialCreateRequest>({
        path: `${root}/${encodeURIComponent(accountId)}/totp/credentials`,
        method: "POST",
        body: request
      });
    },
    replaceTotpCredential(
      accountId: Id,
      credentialId: Id,
      version: Id,
      request: TotpCredentialReplaceRequest
    ): Promise<TotpCredentialChangeView> {
      return client.request<TotpCredentialChangeView, TotpCredentialReplaceRequest>({
        path: `${root}/${encodeURIComponent(accountId)}/totp/credentials/${encodeURIComponent(credentialId)}`,
        method: "PUT",
        headers: { "If-Match": `"${version}"` },
        body: request
      });
    },
    deleteTotpCredential(
      accountId: Id,
      credentialId: Id,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/totp/credentials/${encodeURIComponent(credentialId)}`,
        method: "DELETE",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    },
    createPasskeyRegistration(
      accountId: Id,
      request: PasskeyRegistrationCreateRequest,
      authenticationId: string
    ): Promise<PasskeyRegistrationView> {
      return client.request<PasskeyRegistrationView, PasskeyRegistrationCreateRequest>({
        path: `${root}/${encodeURIComponent(accountId)}/passkey/registrations`,
        method: "POST",
        headers: { "X-Authentication-Id": authenticationId },
        body: request
      });
    },
    activatePasskeyCredential(
      accountId: Id,
      request: PasskeyCredentialCreateRequest
    ): Promise<PasskeyCredentialChangeView> {
      return client.request<PasskeyCredentialChangeView, PasskeyCredentialCreateRequest>({
        path: `${root}/${encodeURIComponent(accountId)}/passkey/credentials`,
        method: "POST",
        body: request
      });
    },
    deletePasskeyCredential(
      accountId: Id,
      credentialId: Id,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/passkey/credentials/${encodeURIComponent(credentialId)}`,
        method: "DELETE",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    },
    endSession(accountId: Id, sessionId: string, authenticationId: string): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/sessions/${encodeURIComponent(sessionId)}`,
        method: "DELETE",
        headers: { "X-Authentication-Id": authenticationId }
      });
    }
  };
}
