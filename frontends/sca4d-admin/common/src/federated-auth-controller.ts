import type { AdminApiClient } from './api-client';
import type {
  FederatedAuthorization,
  FederatedIdentity,
  FederatedProvider,
} from './types';

import { VERIFICATION_HEADER } from './http-contract';

/** 调用应用联邦登录和当前账号绑定接口。 */
export class FederatedAuthController {
  constructor(private readonly api: AdminApiClient) {}

  /** 读取登录页当前可用 Provider。 */
  async loginProviders(): Promise<readonly FederatedProvider[]> {
    const result = await this.api.get<readonly FederatedProvider[]>(
      '/login/federated-providers',
    );
    return result.data;
  }

  /** 开始指定 Provider 的匿名登录授权并返回上游地址。 */
  async startLogin(providerCode: string): Promise<FederatedAuthorization> {
    const result = await this.api.post<FederatedAuthorization>(
      `/login/federated-providers/${encodeURIComponent(providerCode)}/authorizations`,
    );
    return result.data;
  }

  /** 读取当前账号已绑定的联邦身份。 */
  async identities(): Promise<readonly FederatedIdentity[]> {
    const result = await this.api.get<readonly FederatedIdentity[]>(
      '/me/authentication/federated-identities',
    );
    return result.data;
  }

  /** 开始当前账号的 Provider 绑定授权。 */
  async startLink(providerCode: string): Promise<FederatedAuthorization> {
    const result = await this.api.post<FederatedAuthorization>(
      `/me/authentication/federated-identities/${encodeURIComponent(providerCode)}/authorizations`,
    );
    return result.data;
  }

  /** 使用已完成动作认证和 completion id 绑定外部身份。 */
  async link(
    authorizationId: string,
    verificationId: string,
  ): Promise<FederatedIdentity> {
    const result = await this.api.post<FederatedIdentity>(
      '/me/authentication/federated-identities',
      { authorizationId },
      { [VERIFICATION_HEADER]: verificationId },
    );
    return result.data;
  }

  /** 使用已完成动作认证解绑外部身份。 */
  async unlink(
    identity: FederatedIdentity,
    verificationId: string,
  ): Promise<void> {
    await this.api.delete(
      `/me/authentication/federated-identities/${encodeURIComponent(identity.id)}?version=${encodeURIComponent(identity.version)}`,
      { [VERIFICATION_HEADER]: verificationId },
    );
  }
}
