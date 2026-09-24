import type { AuthenticationSettings, Verification } from '@sca4d/admin';

import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useSecurityStore } from './security';

const fixtures = vi.hoisted(() => {
  type Listener = (state: Readonly<Record<string, unknown>>) => void;

  function controller(initial: Record<string, unknown>) {
    const listeners = new Set<Listener>();
    return {
      state: initial,
      publish(next: Record<string, unknown>) {
        this.state = next;
        listeners.forEach((listener) => listener(next));
      },
      subscribe(listener: Listener) {
        listeners.add(listener);
        listener(this.state);
        return () => listeners.delete(listener);
      },
    };
  }

  const authenticationSettings = {
    ...controller({ error: null, phase: 'idle', settings: null }),
    load: vi.fn(),
    replace: vi.fn(),
  };
  const passkeys = {
    ...controller({ credentials: [], error: null, phase: 'idle' }),
    createRegistrationOptions: vi.fn(),
    load: vi.fn(),
    register: vi.fn(),
    remove: vi.fn(),
  };
  const password = {
    ...controller({ error: null, password: null, phase: 'idle' }),
    load: vi.fn(),
    replace: vi.fn(),
  };
  const totp = {
    ...controller({
      credential: null,
      enrollment: null,
      error: null,
      phase: 'idle',
      qrCode: null,
    }),
    activate: vi.fn(),
    cancelEnrollment: vi.fn(),
    createEnrollment: vi.fn(),
    load: vi.fn(),
    remove: vi.fn(),
  };
  const verification = {
    ...controller({ error: null, phase: 'idle', verification: null }),
    clear: vi.fn(() => {
      verification.publish({ error: null, phase: 'idle', verification: null });
    }),
    createPasskeyOptions: vi.fn(),
    start: vi.fn(),
    submitPasskey: vi.fn(),
    submitPassword: vi.fn(),
    submitTotp: vi.fn(),
  };
  const browserPasskeyClient = {
    createCredential: vi.fn(),
    getAssertion: vi.fn(),
    isSupported: vi.fn(() => true),
  };

  return {
    app: { authenticationSettings, passkeys, password, totp, verification },
    authenticationSettings,
    browserPasskeyClient,
    passkeys,
    password,
    totp,
    verification,
  };
});

vi.mock('#/platform/business-application', () => ({
  businessApplication: fixtures.app,
}));
vi.mock('#/auth/passkey', () => ({
  browserPasskeyClient: fixtures.browserPasskeyClient,
}));

const SETTINGS: AuthenticationSettings = {
  loginFactorCount: 1,
  methods: [],
  reusableAuthenticationMaximumAgeSeconds: '300',
  sensitiveActionFactorCount: 1,
};

describe('useSecurityStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    fixtures.verification.publish({
      error: null,
      phase: 'idle',
      verification: null,
    });
  });

  it('近期认证立即满足时执行并完成原动作', async () => {
    const satisfied = verification(0, []);
    fixtures.verification.start.mockImplementation(async () => {
      fixtures.verification.publish({
        error: null,
        phase: 'ready',
        verification: satisfied,
      });
      return satisfied;
    });
    const store = useSecurityStore();

    await expect(store.replaceAuthenticationSettings(SETTINGS)).resolves.toBe(
      'authenticationSettings.replace',
    );

    expect(fixtures.authenticationSettings.replace).toHaveBeenCalledWith(
      SETTINGS,
      satisfied,
    );
    expect(store.pendingAction).toBeNull();
    expect(store.completedAction).toBe('authenticationSettings.replace');
  });

  it('允许在 TOTP 和 Passkey 同时可用时先选择 Passkey 再提交 TOTP', async () => {
    const first = verification(2, [
      'AUTHENTICATION/TOTP',
      'AUTHENTICATION/PASSKEY',
    ]);
    const second = verification(1, ['AUTHENTICATION/TOTP']);
    const satisfied = verification(0, []);
    fixtures.verification.start.mockImplementation(async () => {
      fixtures.verification.publish({
        error: null,
        phase: 'ready',
        verification: first,
      });
      return first;
    });
    fixtures.verification.createPasskeyOptions.mockResolvedValue({
      ceremonyId: 'ceremony-1',
      expiresAt: '2026-08-01T08:00:00Z',
      optionsJson: '{}',
    });
    fixtures.browserPasskeyClient.getAssertion.mockResolvedValue({
      ceremonyId: 'ceremony-1',
      responseJson: '{}',
    });
    fixtures.verification.submitPasskey.mockImplementation(async () => {
      fixtures.verification.publish({
        error: null,
        phase: 'ready',
        verification: second,
      });
      return second;
    });
    fixtures.verification.submitTotp.mockImplementation(async () => {
      fixtures.verification.publish({
        error: null,
        phase: 'ready',
        verification: satisfied,
      });
      return satisfied;
    });
    const store = useSecurityStore();

    await expect(store.replacePassword('new-password')).resolves.toBeNull();
    expect(store.verification.verification?.availableMethods).toEqual([
      'AUTHENTICATION/TOTP',
      'AUTHENTICATION/PASSKEY',
    ]);

    await expect(store.submitPasskey()).resolves.toBeNull();
    expect(fixtures.password.replace).not.toHaveBeenCalled();
    expect(store.verification.verification?.availableMethods).toEqual([
      'AUTHENTICATION/TOTP',
    ]);

    await expect(store.submitTotp('123456')).resolves.toBe('password.replace');
    expect(fixtures.password.replace).toHaveBeenCalledWith(
      'new-password',
      satisfied,
    );
    expect(store.pendingAction).toBeNull();
  });

  it('tOTP enrollment 激活后才发布完成结果', async () => {
    const satisfied = verification(0, []);
    fixtures.verification.start.mockImplementation(async () => {
      fixtures.verification.publish({
        error: null,
        phase: 'ready',
        verification: satisfied,
      });
      return satisfied;
    });
    const store = useSecurityStore();

    await store.beginTotpEnrollment();
    expect(fixtures.totp.createEnrollment).toHaveBeenCalledWith(satisfied);
    expect(store.completedAction).toBeNull();

    await store.activateTotp('123456');
    expect(store.completedAction).toBe('totp.configure');
  });
});

/** 创建动作认证测试使用的公开进度。 */
function verification(
  remainingFactorCount: number,
  availableMethods: readonly string[],
): Verification {
  return {
    availableMethods,
    expiresAt: '2026-08-01T08:00:00Z',
    remainingFactorCount,
    status: remainingFactorCount === 0 ? 'SATISFIED' : 'FACTOR_REQUIRED',
    verificationId: 'verification-1',
  };
}
