import type { PasskeyOptions, PasskeyProof } from '@sca4d/admin';

interface JsonPublicKeyCredential extends Credential {
  toJSON(): unknown;
}

interface PublicKeyCredentialJsonApi {
  parseCreationOptionsFromJSON(
    options: unknown,
  ): PublicKeyCredentialCreationOptions;
  parseRequestOptionsFromJSON(
    options: unknown,
  ): PublicKeyCredentialRequestOptions;
}

/** 浏览器 Passkey 操作失败种类。 */
export type BrowserPasskeyErrorKind = 'cancelled' | 'failed' | 'unsupported';

/** 页面可以稳定区分的浏览器 Passkey 错误。 */
export class BrowserPasskeyError extends Error {
  constructor(readonly kind: BrowserPasskeyErrorKind) {
    super(kind);
    this.name = 'BrowserPasskeyError';
  }
}

/** 使用浏览器 WebAuthn JSON API 完成 assertion 和 registration。 */
export class BrowserPasskeyClient {
  /** 使用浏览器原生 WebAuthn JSON API 完成 registration。 */
  async createCredential(options: PasskeyOptions): Promise<PasskeyProof> {
    const jsonApi = this.requireJsonApi();
    try {
      const credential = (await navigator.credentials.create({
        publicKey: jsonApi.parseCreationOptionsFromJSON(
          publicKeyOptionsJson(options.optionsJson),
        ),
      })) as JsonPublicKeyCredential | null;
      return this.proof(options, credential);
    } catch (error) {
      throw browserPasskeyError(error);
    }
  }

  /** 使用浏览器原生 WebAuthn JSON API 完成 assertion。 */
  async getAssertion(options: PasskeyOptions): Promise<PasskeyProof> {
    const jsonApi = this.requireJsonApi();
    try {
      const credential = (await navigator.credentials.get({
        publicKey: jsonApi.parseRequestOptionsFromJSON(
          publicKeyOptionsJson(options.optionsJson),
        ),
      })) as JsonPublicKeyCredential | null;
      return this.proof(options, credential);
    } catch (error) {
      throw browserPasskeyError(error);
    }
  }

  /** 返回当前浏览器和页面上下文能否执行 Passkey ceremony。 */
  isSupported(): boolean {
    const credential = globalThis.PublicKeyCredential as
      | (PublicKeyCredentialJsonApi & typeof PublicKeyCredential)
      | undefined;
    return Boolean(
      globalThis.isSecureContext &&
      globalThis.navigator?.credentials &&
      credential &&
      typeof credential.parseCreationOptionsFromJSON === 'function' &&
      typeof credential.parseRequestOptionsFromJSON === 'function',
    );
  }

  private proof(
    options: PasskeyOptions,
    credential: JsonPublicKeyCredential | null,
  ): PasskeyProof {
    if (!credential) throw new BrowserPasskeyError('cancelled');
    return {
      ceremonyId: options.ceremonyId,
      responseJson: JSON.stringify(credential.toJSON()),
    };
  }

  private requireJsonApi(): PublicKeyCredentialJsonApi {
    if (!this.isSupported()) throw new BrowserPasskeyError('unsupported');
    return PublicKeyCredential as unknown as PublicKeyCredentialJsonApi;
  }
}

/** Sca4d Server Admin 使用的浏览器 Passkey 客户端。 */
export const browserPasskeyClient = new BrowserPasskeyClient();

function publicKeyOptionsJson(optionsJson: string): unknown {
  const options = JSON.parse(optionsJson) as unknown;
  if (
    options === null ||
    typeof options !== 'object' ||
    !('publicKey' in options)
  ) {
    throw new TypeError('WebAuthn options 缺少 publicKey');
  }
  return options.publicKey;
}

function browserPasskeyError(error: unknown): BrowserPasskeyError {
  if (error instanceof BrowserPasskeyError) return error;
  if (
    error instanceof DOMException &&
    (error.name === 'AbortError' || error.name === 'NotAllowedError')
  ) {
    return new BrowserPasskeyError('cancelled');
  }
  return new BrowserPasskeyError('failed');
}
