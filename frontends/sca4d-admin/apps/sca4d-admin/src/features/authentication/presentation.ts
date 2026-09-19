import type { SecurityAction } from '#/store/security';

import { BrowserPasskeyError } from '#/auth/passkey';
import { $t } from '#/locales';

/** 返回认证方法编码对应的页面名称。 */
export function authenticationMethodLabel(methodCode: string): string {
  if (methodCode === 'password') return $t('page.security.methods.password');
  if (methodCode === 'totp') return $t('page.security.methods.totp');
  if (methodCode === 'passkey') return $t('page.security.methods.passkey');
  if (methodCode === 'email-code') {
    return $t('page.security.methods.emailCode');
  }
  if (methodCode === 'sms-code') {
    return $t('page.security.methods.smsCode');
  }
  if (methodCode === 'federated') {
    return $t('page.security.methods.federated');
  }
  return methodCode;
}

/** 返回安全操作在动作认证窗口中显示的名称。 */
export function securityActionLabel(action: null | SecurityAction): string {
  if (action === 'authenticationSettings.replace') {
    return $t('page.security.actions.replaceSettings');
  }
  if (action === 'passkey.register') {
    return $t('page.security.actions.registerPasskey');
  }
  if (action === 'passkey.remove') {
    return $t('page.security.actions.removePasskey');
  }
  if (action === 'password.replace') {
    return $t('page.security.actions.replacePassword');
  }
  if (action === 'phone.verify') {
    return $t('page.security.actions.verifyPhone');
  }
  if (action === 'totp.configure') {
    return $t('page.security.actions.configureTotp');
  }
  if (action === 'totp.remove') {
    return $t('page.security.actions.removeTotp');
  }
  if (action === 'totp.replace') {
    return $t('page.security.actions.replaceTotp');
  }
  return $t('page.security.verification.title');
}

/** 返回浏览器 Passkey 失败对应的稳定页面文案；非 Passkey 失败返回空值。 */
export function browserPasskeyErrorText(error: unknown): null | string {
  if (!(error instanceof BrowserPasskeyError)) return null;
  if (error.kind === 'unsupported') {
    return $t('page.security.passkeys.unsupported');
  }
  if (error.kind === 'cancelled') {
    return $t('page.security.passkeys.cancelled');
  }
  return $t('page.security.passkeys.failed');
}

/** 返回已经完成的安全操作对应的成功文案。 */
export function securityActionSuccessText(action: SecurityAction): string {
  if (action === 'authenticationSettings.replace') {
    return $t('page.security.success.settingsReplaced');
  }
  if (action === 'passkey.register') {
    return $t('page.security.success.passkeyRegistered');
  }
  if (action === 'passkey.remove') {
    return $t('page.security.success.passkeyRemoved');
  }
  if (action === 'password.replace') {
    return $t('page.security.success.passwordReplaced');
  }
  if (action === 'phone.verify') {
    return $t('page.security.success.phoneVerified');
  }
  if (action === 'totp.configure') {
    return $t('page.security.success.totpConfigured');
  }
  if (action === 'totp.remove') {
    return $t('page.security.success.totpRemoved');
  }
  return $t('page.security.success.totpReplaced');
}
