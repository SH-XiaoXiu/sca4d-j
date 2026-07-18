import type { ApiClient, ApiResult } from "../api-client";
import type {
  Id,
  IdentityProviderCreateInput,
  IdentityProviderStatus,
  IdentityProviderUpdateInput,
  IdentityProviderView,
  LdapProviderConfigurationInput,
  LdapProviderConfigurationView,
  OAuth2ProfilePreviewView,
  OAuth2ProviderConfigurationInput,
  OAuth2ProviderConfigurationView,
  OidcDiscoveryView,
  OidcProviderConfigurationInput,
  OidcProviderConfigurationView
} from "../types";

/** 创建一个作用域中的 IdentityProvider 与协议配置管理请求。 */
export function createIdentityProviderApi(client: ApiClient, root: string) {
  return {
    providers(): Promise<IdentityProviderView[]> {
      return client.get(root);
    },
    create(
      input: IdentityProviderCreateInput,
      idempotencyKey: string
    ): Promise<ApiResult<IdentityProviderView>> {
      return client.requestResult({
        path: root,
        method: "POST",
        body: input,
        headers: { "Idempotency-Key": idempotencyKey }
      });
    },
    update(
      providerId: Id,
      input: IdentityProviderUpdateInput,
      version: Id
    ): Promise<ApiResult<IdentityProviderView>> {
      const providerRoot = `${root}/${encodeURIComponent(providerId)}`;
      return client.requestResult({
        path: providerRoot,
        method: "PUT",
        body: input,
        headers: { "If-Match": `"${version}"` }
      });
    },
    changeStatus(
      providerId: Id,
      status: IdentityProviderStatus,
      version: Id
    ): Promise<ApiResult<IdentityProviderView>> {
      const providerRoot = `${root}/${encodeURIComponent(providerId)}`;
      return client.requestResult({
        path: providerRoot,
        method: "PATCH",
        body: { status },
        headers: { "If-Match": `"${version}"` }
      });
    },
    delete(providerId: Id, version: Id): Promise<ApiResult<void>> {
      const providerRoot = `${root}/${encodeURIComponent(providerId)}`;
      return client.requestResult({
        path: providerRoot,
        method: "DELETE",
        headers: { "If-Match": `"${version}"` }
      });
    },
    oidcConfiguration(providerId: Id): Promise<OidcProviderConfigurationView> {
      return client.get(`${root}/${encodeURIComponent(providerId)}/oidc`);
    },
    replaceOidcConfiguration(
      providerId: Id,
      input: OidcProviderConfigurationInput,
      version: Id
    ): Promise<ApiResult<OidcProviderConfigurationView>> {
      const providerRoot = `${root}/${encodeURIComponent(providerId)}`;
      return client.requestResult({
        path: `${providerRoot}/oidc`,
        method: "PUT",
        body: input,
        headers: { "If-Match": `"${version}"` }
      });
    },
    discoverOidc(providerId: Id): Promise<OidcDiscoveryView> {
      return client.post(`${root}/${encodeURIComponent(providerId)}/oidc/discoveries`);
    },
    oauth2Configuration(providerId: Id): Promise<OAuth2ProviderConfigurationView> {
      return client.get(`${root}/${encodeURIComponent(providerId)}/oauth2`);
    },
    replaceOAuth2Configuration(
      providerId: Id,
      input: OAuth2ProviderConfigurationInput,
      version: Id
    ): Promise<ApiResult<OAuth2ProviderConfigurationView>> {
      const providerRoot = `${root}/${encodeURIComponent(providerId)}`;
      return client.requestResult({
        path: `${providerRoot}/oauth2`,
        method: "PUT",
        body: input,
        headers: { "If-Match": `"${version}"` }
      });
    },
    previewOAuth2Profile(
      providerId: Id,
      profile: unknown,
      auxiliaryProfile?: unknown
    ): Promise<OAuth2ProfilePreviewView> {
      return client.post(`${root}/${encodeURIComponent(providerId)}/oauth2/previews`, {
        profile,
        ...(auxiliaryProfile === undefined ? {} : { auxiliaryProfile })
      });
    },
    ldapConfiguration(providerId: Id): Promise<LdapProviderConfigurationView> {
      return client.get(`${root}/${encodeURIComponent(providerId)}/ldap`);
    },
    replaceLdapConfiguration(
      providerId: Id,
      input: LdapProviderConfigurationInput,
      version: Id
    ): Promise<ApiResult<LdapProviderConfigurationView>> {
      const providerRoot = `${root}/${encodeURIComponent(providerId)}`;
      return client.requestResult({
        path: `${providerRoot}/ldap`,
        method: "PUT",
        body: input,
        headers: { "If-Match": `"${version}"` }
      });
    },
    testLdapConnection(providerId: Id): Promise<void> {
      return client.post(`${root}/${encodeURIComponent(providerId)}/ldap/tests`);
    }
  };
}
