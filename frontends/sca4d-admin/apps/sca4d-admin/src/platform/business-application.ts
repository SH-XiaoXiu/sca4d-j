import type {
  AdminLanguage,
  HttpPort,
  HttpRequest,
  HttpResponse,
  LanguagePort,
  ObjectTransferPort,
  ObjectTransferRequest,
  SessionToken,
  TokenPort,
} from '@sca4d/admin';

import { useAppConfig } from '@vben/hooks';
import { preferences, updatePreferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import {
  AccountsController,
  ActionVerificationController,
  AdminApiClient,
  AuthenticationSettingsController,
  EmailSendersController,
  EmailTemplatesController,
  FederatedProviderController,
  FilesController,
  IotProductsController,
  OperationLogsController,
  PasskeyController,
  PasswordController,
  PermissionsController,
  PreferencesController,
  ProfileController,
  RolesController,
  SmsNotificationsController,
  SystemDictionariesController,
  SystemSettingsController,
  TotpCredentialController,
} from '@sca4d/admin';

import { notifyAuthenticationFailure } from '#/auth/authentication-failure';
import { notifyAuthenticationSessionRefresh } from '#/auth/client-state-refresh';
import { useSessionTokenStore } from '#/store/auth';

/** 使用浏览器 Fetch API 调用 Sca4d Server。 */
class BrowserHttpPort implements HttpPort {
  constructor(private readonly baseUrl: string) {}

  /** 发送 JSON 或二进制请求，并保留状态码和响应头。 */
  async request(request: HttpRequest): Promise<HttpResponse> {
    const url = new URL(
      `${this.baseUrl}${request.path}`,
      window.location.origin,
    );
    Object.entries(request.query ?? {}).forEach(([key, value]) => {
      if (value !== undefined) url.searchParams.set(key, String(value));
    });
    const response = await fetch(url, {
      headers: request.headers,
      method: request.method,
      ...(request.body === undefined
        ? {}
        : {
            body:
              request.body instanceof FormData
                ? request.body
                : JSON.stringify(request.body),
          }),
    });
    const headers: Record<string, string> = {};
    response.headers.forEach((value, key) => {
      headers[key.toLowerCase()] = value;
    });
    if (request.responseType === 'arrayBuffer') {
      return {
        body: await response.arrayBuffer(),
        headers,
        status: response.status,
      };
    }
    const bodyText = await response.text();
    return {
      ...(bodyText ? { body: parseResponseBody(bodyText) } : {}),
      headers,
      status: response.status,
    };
  }
}

/** 让业务 Controller 与 Vben 认证 Store 读取同一份 Session Token。 */
class VbenTokenPort implements TokenPort {
  /** 清除当前 Session，并让路由返回登录页。 */
  clear(): void {
    void notifyAuthenticationFailure();
  }

  /** 读取 Vben 持久化的完整 Session Token。 */
  read(): null | SessionToken {
    return useSessionTokenStore().tokens;
  }

  /** 替换完整 Session Token，并同步普通请求使用的 access Token。 */
  write(token: SessionToken): void {
    useSessionTokenStore().setTokens(token);
    useAccessStore().setAccessToken(token.accessToken);
  }
}

/** 让业务请求跟随 Vben 当前语言。 */
class VbenLanguagePort implements LanguagePort {
  /** 返回后续请求使用的 `Accept-Language`。 */
  read(): AdminLanguage {
    return preferences.app.locale;
  }

  /** 修改 Vben 当前语言和后续请求语言。 */
  write(language: AdminLanguage): void {
    updatePreferences({ app: { locale: language } });
  }
}

/** 原样执行后端签发的对象上传请求，不附加管理端 Token。 */
class BrowserObjectTransferPort implements ObjectTransferPort {
  /** 上传文件字节；非 2xx 响应直接作为失败返回。 */
  async transfer(request: ObjectTransferRequest): Promise<void> {
    const response = await fetch(new URL(request.url, window.location.origin), {
      body: request.body,
      headers: request.headers,
      method: request.method,
    });
    if (!response.ok) {
      throw new Error(`Object transfer failed with ${response.status}`);
    }
  }
}

/** Sca4d Server 示例业务页面使用的 Controller 集合。 */
export interface BusinessApplication {
  readonly accounts: AccountsController;
  readonly authenticationSettings: AuthenticationSettingsController;
  readonly emailSenders: EmailSendersController;
  readonly emailTemplates: EmailTemplatesController;
  readonly files: FilesController;
  readonly federatedProviders: FederatedProviderController;
  readonly iotProducts: IotProductsController;
  readonly operationLogs: OperationLogsController;
  readonly passkeys: PasskeyController;
  readonly password: PasswordController;
  readonly permissions: PermissionsController;
  readonly profile: ProfileController;
  readonly preferences: PreferencesController;
  readonly roles: RolesController;
  readonly smsNotifications: SmsNotificationsController;
  readonly systemDictionaries: SystemDictionariesController;
  readonly systemSettings: SystemSettingsController;
  readonly totp: TotpCredentialController;
  readonly verification: ActionVerificationController;
}

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const api = new AdminApiClient(
  new BrowserHttpPort(apiURL),
  new VbenTokenPort(),
  new VbenLanguagePort(),
  notifyAuthenticationSessionRefresh,
);

/** 已连接 Vben Token、语言和浏览器传输实现的业务 Controller。 */
export const businessApplication: BusinessApplication = Object.freeze({
  accounts: new AccountsController(api),
  authenticationSettings: new AuthenticationSettingsController(api),
  emailSenders: new EmailSendersController(api),
  emailTemplates: new EmailTemplatesController(api),
  files: new FilesController(
    api,
    new BrowserObjectTransferPort(),
    () => useSessionTokenStore().tokens?.sessionId ?? 'anonymous',
  ),
  federatedProviders: new FederatedProviderController(api),
  iotProducts: new IotProductsController(api),
  operationLogs: new OperationLogsController(api),
  passkeys: new PasskeyController(api),
  password: new PasswordController(
    api,
    () => void notifyAuthenticationFailure(),
  ),
  permissions: new PermissionsController(api),
  profile: new ProfileController(api),
  preferences: new PreferencesController(api),
  roles: new RolesController(api),
  smsNotifications: new SmsNotificationsController(api),
  systemDictionaries: new SystemDictionariesController(api),
  systemSettings: new SystemSettingsController(api),
  totp: new TotpCredentialController(
    api,
    () => void notifyAuthenticationFailure(),
  ),
  verification: new ActionVerificationController(api),
});

/** 把响应正文解析为 JSON；非 JSON 错误正文保持原字符串。 */
function parseResponseBody(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}
