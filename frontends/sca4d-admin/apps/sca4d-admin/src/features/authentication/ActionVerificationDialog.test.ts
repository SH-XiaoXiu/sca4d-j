// @vitest-environment happy-dom

import { createApp, nextTick, reactive } from 'vue';

import Antd from 'ant-design-vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ActionVerificationDialog from './ActionVerificationDialog.vue';

const security = reactive({
  cancelVerification: vi.fn(),
  passkeySupported: false,
  pendingAction: 'profile.email.replace',
  submitPasskey: vi.fn(),
  submitPassword: vi.fn(),
  submitTotp: vi.fn(),
  verification: {
    error: null,
    phase: 'ready',
    verification: {
      availableMethods: ['AUTHENTICATION/PASSWORD'],
      remainingFactorCount: 1,
    },
  },
  verificationOpen: true,
});

vi.mock('#/store/security', () => ({ useSecurityStore: () => security }));
vi.mock('#/locales', () => ({ $t: (key: string) => key }));
vi.mock('#/features/sj-presentation', () => ({
  useSjPresentation: () => ({
    t: (key: string) => key,
    errorText: () => 'Verification failed',
  }),
}));
vi.mock('#/components/sj', async () => {
  const { defineComponent } = await import('vue');
  return {
    SjDialog: defineComponent({
      props: { open: Boolean, saving: Boolean, submitDisabled: Boolean },
      emits: ['submit'],
      template:
        '<div v-if="open"><slot /><button data-test="verification-submit" :disabled="submitDisabled || saving" @click="$emit(\'submit\')">Submit</button></div>',
    }),
  };
});
vi.mock('./presentation', () => ({
  authenticationMethodLabel: (method: string) => method,
  browserPasskeyErrorText: () => undefined,
  securityActionLabel: (action: string) => action,
}));
let app: ReturnType<typeof createApp> | undefined;
let host: HTMLDivElement;

beforeEach(() => {
  vi.clearAllMocks();
  security.verification.phase = 'ready';
  host = document.createElement('div');
  document.body.append(host);
  app = createApp(ActionVerificationDialog);
  app.use(Antd);
  app.mount(host);
});

afterEach(() => {
  app?.unmount();
  host.remove();
});

describe('action verification dialog', () => {
  it('认证请求进行中不会重复提交同一密码', async () => {
    security.submitPassword.mockImplementation(async () => {
      security.verification.phase = 'loading';
    });
    await nextTick();
    const input = document.querySelector<HTMLInputElement>(
      'input[type="password"]',
    )!;
    input.value = 'test-only-password';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();

    const confirm = document.querySelector<HTMLButtonElement>(
      '[data-test="verification-submit"]',
    )!;
    confirm.click();
    confirm.click();
    await nextTick();

    expect(security.submitPassword).toHaveBeenCalledTimes(1);
    expect(security.submitPassword).toHaveBeenCalledWith('test-only-password');
  });
});
