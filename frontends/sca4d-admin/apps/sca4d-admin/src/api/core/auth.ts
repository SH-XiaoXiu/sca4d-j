import type {
  CurrentSession,
  EmailCodeChallenge,
  EmailCodeChallengeRequest,
  EmailCodeProofRequest,
  FederatedAuthorization,
  FederatedProvider,
  FileAccessUrl,
  Int64String,
  LoginOutcome,
  PasskeyOptions,
  PasskeyProof,
  PasswordLoginRequest,
  PasswordProofRequest,
  Profile,
  SessionToken,
  SmsCodeChallenge,
  SmsCodeChallengeRequest,
  SmsCodeProofRequest,
  TotpProofRequest,
} from '@sca4d/admin';

import { baseRequestClient, requestClient } from '#/api/request';

/** 使用 Sca4d Server 允许的账号标识和密码创建登录 attempt。 */
export async function createLoginAttemptApi(data: PasswordLoginRequest) {
  return baseRequestClient.post<LoginOutcome>('/login/attempts', data);
}

/** 查询后端当前启用的联邦登录 Provider。 */
export async function getFederatedProvidersApi() {
  return baseRequestClient.get<readonly FederatedProvider[]>(
    '/login/federated-providers',
  );
}

/** 为指定 Provider 创建匿名联邦授权地址。 */
export async function createFederatedAuthorizationApi(providerCode: string) {
  return baseRequestClient.post<FederatedAuthorization>(
    `/login/federated-providers/${encodeURIComponent(providerCode)}/authorizations`,
  );
}

/** 使用 callback completion id 创建联邦登录 attempt。 */
export async function createFederatedLoginAttemptApi(authorizationId: string) {
  return baseRequestClient.post<LoginOutcome>('/login/attempts/federated', {
    authorizationId,
  });
}

/** 向已有登录 attempt 提交联邦 completion id。 */
export async function submitFederatedLoginApi(
  attemptId: string,
  authorizationId: string,
) {
  return baseRequestClient.post<LoginOutcome>(
    `/login/attempts/${encodeURIComponent(attemptId)}/federated/proofs`,
    { authorizationId },
  );
}

/** 向指定登录 attempt 提交六位 TOTP 验证码。 */
export async function submitLoginTotpApi(
  attemptId: string,
  data: TotpProofRequest,
) {
  return baseRequestClient.post<LoginOutcome>(
    `/login/attempts/${encodeURIComponent(attemptId)}/totp/proofs`,
    data,
  );
}

/** 使用邮箱签发匿名一致的 Email Code 登录 Challenge。 */
export async function createEmailCodeChallengeApi(
  data: EmailCodeChallengeRequest,
) {
  return baseRequestClient.post<EmailCodeChallenge>(
    '/login/email-code/challenges',
    data,
  );
}

/** 使用已消费的 Email Code Challenge 开始登录。 */
export async function createEmailCodeLoginAttemptApi(
  data: EmailCodeProofRequest,
) {
  return baseRequestClient.post<LoginOutcome>('/login/email-code/proofs', data);
}

/** 为已经确定账号的登录 attempt 签发 Email Code Challenge。 */
export async function createPendingEmailCodeChallengeApi(attemptId: string) {
  return baseRequestClient.post<EmailCodeChallenge>(
    `/login/attempts/${encodeURIComponent(attemptId)}/email-code/challenges`,
  );
}

/** 向已有登录 attempt 提交 Email Code。 */
export async function submitLoginEmailCodeApi(
  attemptId: string,
  data: EmailCodeProofRequest,
) {
  return baseRequestClient.post<LoginOutcome>(
    `/login/attempts/${encodeURIComponent(attemptId)}/email-code/proofs`,
    data,
  );
}

/** 使用手机号签发匿名一致的 SMS Code 登录 Challenge。 */
export async function createSmsCodeChallengeApi(data: SmsCodeChallengeRequest) {
  return baseRequestClient.post<SmsCodeChallenge>(
    '/login/sms-code/challenges',
    data,
  );
}

/** 使用已消费的 SMS Code Challenge 开始登录。 */
export async function createSmsCodeLoginAttemptApi(data: SmsCodeProofRequest) {
  return baseRequestClient.post<LoginOutcome>('/login/sms-code/proofs', data);
}

/** 为已经确定账号的登录 attempt 签发 SMS Code Challenge。 */
export async function createPendingSmsCodeChallengeApi(attemptId: string) {
  return baseRequestClient.post<SmsCodeChallenge>(
    `/login/attempts/${encodeURIComponent(attemptId)}/sms-code/challenges`,
  );
}

/** 向已有登录 attempt 提交 SMS Code。 */
export async function submitLoginSmsCodeApi(
  attemptId: string,
  data: SmsCodeProofRequest,
) {
  return baseRequestClient.post<LoginOutcome>(
    `/login/attempts/${encodeURIComponent(attemptId)}/sms-code/proofs`,
    data,
  );
}

/** 向已有登录 attempt 提交密码。 */
export async function submitLoginPasswordApi(
  attemptId: string,
  data: PasswordProofRequest,
) {
  return baseRequestClient.post<LoginOutcome>(
    `/login/attempts/${encodeURIComponent(attemptId)}/password/proofs`,
    data,
  );
}

/** 开始 discoverable Passkey assertion。 */
export async function createPasskeyAssertionOptionsApi() {
  return baseRequestClient.post<PasskeyOptions>('/passkey/assertions/options');
}

/** 使用 Passkey assertion 开始登录。 */
export async function createPasskeyLoginAttemptApi(data: PasskeyProof) {
  return baseRequestClient.post<LoginOutcome>('/login/attempts/passkey', data);
}

/** 向已有登录 attempt 提交 Passkey assertion。 */
export async function submitLoginPasskeyApi(
  attemptId: string,
  data: PasskeyProof,
) {
  return baseRequestClient.post<LoginOutcome>(
    `/login/attempts/${encodeURIComponent(attemptId)}/passkey/proofs`,
    data,
  );
}

/** 使用 access Token 读取当前 Session 和权限编码。 */
export async function getCurrentSessionApi() {
  return requestClient.get<CurrentSession>('/sessions/current');
}

/** 使用 access Token 读取当前账号的显示名称和联系方式。 */
export async function getCurrentProfileApi() {
  return requestClient.get<Profile>('/me/profile');
}

/** 使用 access Token 批量签发当前账号头像的短期读取地址。 */
export async function getFileAccessUrlsApi(fileIds: readonly Int64String[]) {
  return requestClient.post<readonly FileAccessUrl[]>('/files/access-urls', {
    fileIds,
  });
}

/** 使用 backing Session Token 签发新的 access Token。 */
export async function refreshSessionTokensApi(sessionToken: string) {
  return baseRequestClient.post<SessionToken>(
    '/sessions/current/tokens',
    undefined,
    { headers: { Authorization: `Bearer ${sessionToken}` } },
  );
}

/** 使用 backing Session Token 注销当前 Session。 */
export async function logoutSessionApi(sessionToken: string) {
  return baseRequestClient.delete<unknown>('/sessions/current', {
    headers: { Authorization: `Bearer ${sessionToken}` },
  });
}
