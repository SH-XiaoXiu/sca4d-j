/** 动作认证完成后提交单次验证标识的 Header。 */
export const VERIFICATION_HEADER = 'X-Verification';

/** 认证进度和账号设置公开的 TreeCode 方法编码。 */
export const AUTHENTICATION_METHOD_CODES = {
  emailCode: 'AUTHENTICATION/EMAIL_CODE',
  federated: 'AUTHENTICATION/FEDERATED',
  passkey: 'AUTHENTICATION/PASSKEY',
  password: 'AUTHENTICATION/PASSWORD',
  smsCode: 'AUTHENTICATION/SMS_CODE',
  totp: 'AUTHENTICATION/TOTP',
} as const;
