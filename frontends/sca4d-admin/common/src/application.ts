import type {
  HttpPort,
  LanguagePort,
  ObjectTransferPort,
  RealtimePort,
  TokenPort,
} from './ports';

import { ActionVerificationController } from './action-verification-controller';
import { AdminApiClient } from './api-client';
import { AuthenticationSettingsController } from './authentication-settings-controller';
import { ObservableController } from './controller';
import {
  EmailSendersController,
  EmailTemplatesController,
} from './email-notification-controllers';
import { FederatedAuthController } from './federated-auth-controller';
import { FederatedProviderController } from './federated-provider-controller';
import { FilesController } from './files-controller';
import { InboxController } from './inbox-controller';
import { IotProductsController } from './iot-products-controller';
import { NotificationSettingsController } from './notification-settings-controller';
import { OperationLogsController } from './operation-logs-controller';
import { PasskeyController } from './passkey-controller';
import { PreferencesController } from './preferences-controller';
import {
  AccountsController,
  PasswordController,
  PermissionsController,
  ProfileController,
  RolesController,
} from './resource-controllers';
import { SessionController } from './session-controller';
import { SmsNotificationsController } from './sms-notification-controller';
import {
  SystemDictionariesController,
  SystemSettingsController,
} from './system-controllers';
import { TotpCredentialController } from './totp-credential-controller';

/** 创建纯 TypeScript 管理端所需的宿主 Ports。 */
export interface AdminApplicationPorts {
  readonly http: HttpPort;
  readonly languages: LanguagePort;
  readonly objectTransfers: ObjectTransferPort;
  readonly realtime: RealtimePort;
  readonly tokens: TokenPort;
}

/** 管理端全部业务 Controller。 */
export interface AdminApplication {
  readonly accounts: AccountsController;
  readonly authenticationSettings: AuthenticationSettingsController;
  readonly emailSenders: EmailSendersController;
  readonly emailTemplates: EmailTemplatesController;
  readonly federatedAuth: FederatedAuthController;
  readonly federatedProviders: FederatedProviderController;
  readonly files: FilesController;
  readonly inbox: InboxController;
  readonly iotProducts: IotProductsController;
  readonly notificationSettings: NotificationSettingsController;
  readonly operationLogs: OperationLogsController;
  readonly passkeys: PasskeyController;
  readonly password: PasswordController;
  readonly permissions: PermissionsController;
  readonly preferences: PreferencesController;
  readonly profile: ProfileController;
  readonly roles: RolesController;
  readonly session: SessionController;
  readonly smsNotifications: SmsNotificationsController;
  readonly systemDictionaries: SystemDictionariesController;
  readonly systemSettings: SystemSettingsController;
  readonly totp: TotpCredentialController;
  readonly verification: ActionVerificationController;
}

/** 使用宿主 Ports 创建互不依赖 Vue、Pinia、Router 和 DOM 的管理端。 */
export function createAdminApplication(
  ports: AdminApplicationPorts,
): AdminApplication {
  const controllers: { session?: SessionController } = {};
  const api = new AdminApiClient(
    ports.http,
    ports.tokens,
    ports.languages,
    () => controllers.session?.refreshFromServer(),
  );
  const session = new SessionController(api, ports.tokens, ports.realtime);
  const files = new FilesController(
    api,
    ports.objectTransfers,
    () => ports.tokens.read()?.sessionId ?? 'anonymous',
  );
  controllers.session = session;
  const application = Object.freeze({
    accounts: new AccountsController(api),
    inbox: new InboxController(api),
    notificationSettings: new NotificationSettingsController(api),
    authenticationSettings: new AuthenticationSettingsController(api),
    emailSenders: new EmailSendersController(api),
    emailTemplates: new EmailTemplatesController(api),
    files,
    federatedAuth: new FederatedAuthController(api),
    federatedProviders: new FederatedProviderController(api),
    operationLogs: new OperationLogsController(api),
    iotProducts: new IotProductsController(api),
    passkeys: new PasskeyController(api),
    password: new PasswordController(api, () => session.clearSession()),
    permissions: new PermissionsController(api),
    profile: new ProfileController(api),
    preferences: new PreferencesController(api),
    roles: new RolesController(api),
    session,
    smsNotifications: new SmsNotificationsController(api),
    systemDictionaries: new SystemDictionariesController(api),
    systemSettings: new SystemSettingsController(api),
    totp: new TotpCredentialController(api, () => session.clearSession()),
    verification: new ActionVerificationController(api),
  });
  let resourceSession = ports.tokens.read()?.sessionId;
  session.subscribe(() => {
    const current = ports.tokens.read()?.sessionId;
    if (resourceSession === current) return;
    resourceSession = current;
    files.clear();
    for (const controller of Object.values(application)) {
      if (controller !== session && controller instanceof ObservableController)
        controller.resetState();
    }
  });
  return application;
}
