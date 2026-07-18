import type { ApiClient } from "../api-client";
import type {
  AccountScopeType,
  AuthenticationProcess,
  AuthenticationCreateRequest,
  AuthMethod,
  LoginSession,
  LdapAvailableProvider,
  LdapProofRequest,
  OAuth2Authorization,
  OAuth2AuthorizationCreateRequest,
  OAuth2AvailableProvider,
  OidcAuthorization,
  OidcAuthorizationCreateRequest,
  OidcAvailableProvider,
  PasskeyAssertionView,
  PasskeyProofRequest,
  PasswordProofRequest,
  PasswordReplacementRequest,
  RecoveryCodeProofRequest,
  TotpProofRequest,
  EmailCodeChallenge,
  EmailCodeProofRequest
} from "../types";

/** 创建认证、提交各认证方式的输入、创建会话和退出的请求方法。 */
export function createAuthenticationApi(client: ApiClient) {
  const api = {
    methods(): Promise<AuthMethod[]> {
      return client.get<AuthMethod[]>("/authentication-methods");
    },
    createAuthentication(request: AuthenticationCreateRequest): Promise<AuthenticationProcess> {
      return client.post<AuthenticationProcess, AuthenticationCreateRequest>("/authentications", request);
    },
    oidcProviders(scopeType: AccountScopeType, tenantCode?: string): Promise<OidcAvailableProvider[]> {
      return client.get<OidcAvailableProvider[]>("/auth/oidc/providers", { scopeType, tenantCode });
    },
    createOidcAuthorization(
      authenticationId: string,
      request: OidcAuthorizationCreateRequest
    ): Promise<OidcAuthorization> {
      return client.request<OidcAuthorization, OidcAuthorizationCreateRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/oidc-authorizations`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    oauth2Providers(scopeType: AccountScopeType, tenantCode?: string): Promise<OAuth2AvailableProvider[]> {
      return client.get<OAuth2AvailableProvider[]>("/auth/oauth2/providers", { scopeType, tenantCode });
    },
    createOAuth2Authorization(
      authenticationId: string,
      request: OAuth2AuthorizationCreateRequest
    ): Promise<OAuth2Authorization> {
      return client.request<OAuth2Authorization, OAuth2AuthorizationCreateRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/oauth2-authorizations`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    ldapProviders(scopeType: AccountScopeType, tenantCode?: string): Promise<LdapAvailableProvider[]> {
      return client.get<LdapAvailableProvider[]>("/auth/ldap/providers", { scopeType, tenantCode });
    },
    ldapProof(authenticationId: string, request: LdapProofRequest): Promise<AuthenticationProcess> {
      return client.request<AuthenticationProcess, LdapProofRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/ldap-proofs`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    passwordProof(authenticationId: string, request: PasswordProofRequest): Promise<AuthenticationProcess> {
      return client.request<AuthenticationProcess, PasswordProofRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/password-proofs`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    recoveryCodeProof(
      authenticationId: string,
      request: RecoveryCodeProofRequest
    ): Promise<AuthenticationProcess> {
      return client.request<AuthenticationProcess, RecoveryCodeProofRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/recovery-code-proofs`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    replacePassword(authenticationId: string, request: PasswordReplacementRequest): Promise<void> {
      return client.request<void, PasswordReplacementRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/password-replacements`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    createEmailCodeChallenge(authenticationId: string): Promise<EmailCodeChallenge> {
      return client.request<EmailCodeChallenge>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/email-code-challenges`,
        method: "POST",
        preserveSessionOnUnauthorized: true
      });
    },
    emailCodeProof(
      authenticationId: string,
      request: EmailCodeProofRequest
    ): Promise<AuthenticationProcess> {
      return client.request<AuthenticationProcess, EmailCodeProofRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/email-code-proofs`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    totpProof(authenticationId: string, request: TotpProofRequest): Promise<AuthenticationProcess> {
      return client.request<AuthenticationProcess, TotpProofRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/totp-proofs`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    createPasskeyAssertion(authenticationId: string): Promise<PasskeyAssertionView> {
      return client.request<PasskeyAssertionView>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/passkey-assertions`,
        method: "POST",
        preserveSessionOnUnauthorized: true
      });
    },
    passkeyProof(authenticationId: string, request: PasskeyProofRequest): Promise<AuthenticationProcess> {
      return client.request<AuthenticationProcess, PasskeyProofRequest>({
        path: `/authentications/${encodeURIComponent(authenticationId)}/passkey-proofs`,
        method: "POST",
        body: request,
        preserveSessionOnUnauthorized: true
      });
    },
    createSession(authenticationId: string): Promise<LoginSession> {
      return client.request<LoginSession, { authenticationId: string }>({
        path: "/sessions",
        method: "POST",
        body: { authenticationId },
        preserveSessionOnUnauthorized: true
      });
    },
    logout(): Promise<void> {
      return client.delete<void>("/sessions/current");
    }
  };
  return api;
}
