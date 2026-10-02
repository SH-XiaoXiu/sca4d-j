import type { AUTHENTICATION_METHOD_CODES } from '@sca4d/admin';

/** 当前认证操作的一份输入；Passkey 的浏览器交互由调用方执行。 */
export type AuthenticationProofSubmission =
  | {
      readonly code: string;
      readonly methodCode:
        | typeof AUTHENTICATION_METHOD_CODES.emailCode
        | typeof AUTHENTICATION_METHOD_CODES.smsCode
        | typeof AUTHENTICATION_METHOD_CODES.totp;
    }
  | { readonly methodCode: typeof AUTHENTICATION_METHOD_CODES.passkey }
  | {
      readonly methodCode: typeof AUTHENTICATION_METHOD_CODES.password;
      readonly password: string;
    };

/** 发送验证码后可以公开展示的收件人与重发时间，不包含 Challenge 标识。 */
export interface AuthenticationCodeNotice {
  readonly maskedRecipient: string;
  readonly resendAt: string;
}
