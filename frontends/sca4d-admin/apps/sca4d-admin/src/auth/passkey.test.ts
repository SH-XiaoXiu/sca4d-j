import type { PasskeyOptions } from '@sca4d/admin';

import { afterEach, describe, expect, it, vi } from 'vitest';

import { BrowserPasskeyClient, BrowserPasskeyError } from './passkey';

const OPTIONS: PasskeyOptions = {
  ceremonyId: 'ceremony-1',
  expiresAt: '2026-08-01T08:00:00Z',
  optionsJson: JSON.stringify({
    publicKey: { challenge: 'Y2hhbGxlbmdl' },
  }),
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('browserPasskeyClient', () => {
  it('在 WebAuthn JSON API 不可用时拒绝 ceremony', async () => {
    vi.stubGlobal('isSecureContext', false);
    const client = new BrowserPasskeyClient();

    expect(client.isSupported()).toBe(false);
    await expect(client.getAssertion(OPTIONS)).rejects.toEqual(
      new BrowserPasskeyError('unsupported'),
    );
  });

  it('解析 assertion options 并提交 credential JSON', async () => {
    const parsedOptions = { challenge: new Uint8Array([1, 2, 3]) };
    const parseRequestOptionsFromJSON = vi.fn(() => parsedOptions);
    const get = vi.fn(async () => ({
      toJSON: () => ({ id: 'credential-1', type: 'public-key' }),
    }));
    installWebAuthn({ get, parseRequestOptionsFromJSON });

    const proof = await new BrowserPasskeyClient().getAssertion(OPTIONS);

    expect(parseRequestOptionsFromJSON).toHaveBeenCalledWith({
      challenge: 'Y2hhbGxlbmdl',
    });
    expect(get).toHaveBeenCalledWith({ publicKey: parsedOptions });
    expect(proof).toEqual({
      ceremonyId: 'ceremony-1',
      responseJson: JSON.stringify({ id: 'credential-1', type: 'public-key' }),
    });
  });

  it('解析 registration options 并提交 credential JSON', async () => {
    const parsedOptions = { challenge: new Uint8Array([4, 5, 6]) };
    const parseCreationOptionsFromJSON = vi.fn(() => parsedOptions);
    const create = vi.fn(async () => ({
      toJSON: () => ({ id: 'credential-2', type: 'public-key' }),
    }));
    installWebAuthn({ create, parseCreationOptionsFromJSON });

    const proof = await new BrowserPasskeyClient().createCredential(OPTIONS);

    expect(parseCreationOptionsFromJSON).toHaveBeenCalledWith({
      challenge: 'Y2hhbGxlbmdl',
    });
    expect(create).toHaveBeenCalledWith({ publicKey: parsedOptions });
    expect(JSON.parse(proof.responseJson)).toEqual({
      id: 'credential-2',
      type: 'public-key',
    });
  });

  it('把浏览器取消 ceremony 转换为稳定错误', async () => {
    const get = vi.fn(async () => {
      throw new DOMException('cancelled', 'NotAllowedError');
    });
    installWebAuthn({ get });

    await expect(
      new BrowserPasskeyClient().getAssertion(OPTIONS),
    ).rejects.toEqual(new BrowserPasskeyError('cancelled'));
  });
});

interface WebAuthnStubs {
  create?: (options: CredentialCreationOptions) => Promise<unknown>;
  get?: (options: CredentialRequestOptions) => Promise<unknown>;
  parseCreationOptionsFromJSON?: (options: unknown) => unknown;
  parseRequestOptionsFromJSON?: (options: unknown) => unknown;
}

/** 安装单个测试需要的 WebAuthn JSON API 和 Credential Management API。 */
function installWebAuthn(stubs: WebAuthnStubs): void {
  vi.stubGlobal('isSecureContext', true);
  vi.stubGlobal('PublicKeyCredential', {
    parseCreationOptionsFromJSON:
      stubs.parseCreationOptionsFromJSON ?? vi.fn((value) => value),
    parseRequestOptionsFromJSON:
      stubs.parseRequestOptionsFromJSON ?? vi.fn((value) => value),
  });
  vi.stubGlobal('navigator', {
    credentials: {
      create: stubs.create ?? vi.fn(),
      get: stubs.get ?? vi.fn(),
    },
  });
}
