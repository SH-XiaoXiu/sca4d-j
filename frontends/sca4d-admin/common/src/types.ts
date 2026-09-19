import type { SessionToken } from './ports';

/** 后端统一响应外壳。 */
export interface ApiResponse<T> {
  readonly callId?: null | string;
  readonly code: string;
  readonly data: T;
  readonly message?: null | string;
  readonly messageArgs?: readonly unknown[];
  readonly messageKey?: null | string;
  readonly timestamp?: string;
}

/** Java `long` 和 `Long` 在 JSON 中使用的十进制字符串。 */
export type Int64String = string;

/** HTTP 设置和偏好接口可以完整传输的 JSON 值。 */
export type JsonValue =
  | boolean
  | null
  | number
  | string
  | readonly JsonValue[]
  | { readonly [key: string]: JsonValue };

/** 密码登录请求。 */
export interface PasswordLoginRequest {
  readonly identifier: string;
  readonly password: string;
}

/** 六位 TOTP 验证码请求。 */
export interface TotpProofRequest {
  readonly code: string;
}

/** 使用邮箱签发匿名一致 Challenge 的请求。 */
export interface EmailCodeChallengeRequest {
  readonly email: string;
}

/** 六位 Email 验证码请求。 */
export interface EmailCodeProofRequest {
  readonly challengeId: string;
  readonly code: string;
}

/** Email 验证码签发结果，不包含完整邮箱或验证码。 */
export interface EmailCodeChallenge {
  readonly challengeId: string;
  readonly expiresAt: string;
  readonly maskedRecipient: string;
  readonly resendAt: string;
}

/** 使用手机号签发匿名一致 SMS Challenge 的请求。 */
export interface SmsCodeChallengeRequest {
  readonly phoneNumber: string;
}

/** 六位 SMS 验证码请求。 */
export interface SmsCodeProofRequest {
  readonly challengeId: string;
  readonly code: string;
}

/** SMS 验证码签发结果，不包含完整手机号或验证码。 */
export interface SmsCodeChallenge {
  readonly challengeId: string;
  readonly expiresAt: string;
  readonly maskedRecipient: string;
  readonly resendAt: string;
}

/** 向已有登录 attempt 提交密码的请求。 */
export interface PasswordProofRequest {
  readonly password: string;
}

/** 动作认证 attempt 的公开进度。 */
export interface AuthenticationProgress {
  readonly attemptId: string;
  readonly availableMethods: readonly string[];
  readonly expiresAt: string;
  readonly remainingFactorCount: number;
  readonly status: 'FACTOR_REQUIRED' | 'SATISFIED';
}

/** 敏感 verification 的公开进度。 */
export interface Verification {
  readonly availableMethods: readonly string[];
  readonly expiresAt: string;
  readonly remainingFactorCount: number;
  readonly status: 'FACTOR_REQUIRED' | 'SATISFIED';
  readonly verificationId: string;
}

/** 登录 attempt 当前结果。 */
export interface LoginOutcome {
  readonly authentication: AuthenticationProgress;
  readonly session: null | SessionToken;
}

/** 登录页由后端返回的可用联邦 Provider。 */
export interface FederatedProvider {
  readonly buttonText: string;
  readonly code: string;
  readonly displayOrder: number;
  readonly iconReference: null | string;
  readonly id: Int64String;
  readonly loginPageVisible: boolean;
  readonly name: string;
  readonly protocol: 'OAUTH2' | 'OIDC';
  readonly status: 'ACTIVE' | 'DISABLED';
  readonly version: Int64String;
}

/** 后台读取到的不含 Client Secret 的 OIDC/OAuth2 协议配置。 */
export interface FederatedProviderSettings {
  readonly authorizationEndpoint: null | string;
  readonly authorizationParameters: Readonly<Record<string, string>>;
  readonly clientAuthentication:
    | 'CLIENT_SECRET_BASIC'
    | 'CLIENT_SECRET_POST'
    | 'NONE';
  readonly clientId: null | string;
  readonly clientSecretConfigured: boolean;
  readonly issuer: null | string;
  readonly jwksEndpoint: null | string;
  readonly profileMapping: Readonly<Record<string, string>>;
  readonly scopes: readonly string[];
  readonly tokenEndpoint: null | string;
  readonly userInfoEndpoint: null | string;
}

/** 创建联邦 Provider 的展示信息。新建 Provider 默认停用。 */
export interface CreateFederatedProviderInput {
  readonly code: string;
  readonly displayOrder: number;
  readonly iconReference: null | string;
  readonly i18n: LocalizedFields;
  readonly loginPageVisible: boolean;
  readonly protocol: 'OAUTH2' | 'OIDC';
}

/** 替换联邦 Provider 的登录页展示信息。 */
export interface ReplaceFederatedProviderInput {
  readonly displayOrder: number;
  readonly iconReference: null | string;
  readonly i18n: LocalizedFields;
  readonly loginPageVisible: boolean;
}

/** 替换联邦 Provider 协议配置时提交的字段；空 Secret 保留已保存值。 */
export interface FederatedProviderSettingsInput {
  readonly authorizationEndpoint: null | string;
  readonly authorizationParameters: Readonly<Record<string, string>>;
  readonly clientAuthentication:
    | 'CLIENT_SECRET_BASIC'
    | 'CLIENT_SECRET_POST'
    | 'NONE';
  readonly clientId: null | string;
  readonly clientSecret: null | string;
  readonly issuer: null | string;
  readonly jwksEndpoint: null | string;
  readonly profileMapping: Readonly<Record<string, string>>;
  readonly scopes: readonly string[];
  readonly tokenEndpoint: null | string;
  readonly userInfoEndpoint: null | string;
}

/** 当前账号已绑定的联邦身份快照。 */
export interface FederatedIdentity {
  readonly accountId: Int64String;
  readonly avatarUrl: null | string;
  readonly displayName: null | string;
  readonly email: null | string;
  readonly emailVerified: boolean;
  readonly id: Int64String;
  readonly issuer: null | string;
  readonly providerId: Int64String;
  readonly subject: string;
  readonly version: Int64String;
}

/** 联邦授权开始结果。 */
export interface FederatedAuthorization {
  readonly authorizationUrl: string;
}

/** 当前 Session 与生效权限。 */
export interface CurrentSession {
  readonly account: {
    readonly accountId: Int64String;
    readonly realmKey: string;
  };
  readonly expiresAt: string;
  readonly issuedAt: string;
  readonly permissions: readonly string[];
  readonly sessionId: string;
  readonly subject: { readonly subjectId: Int64String; readonly type: string };
}

/** Platform Account 状态。 */
export type AccountStatus = 'ACTIVE' | 'DISABLED';

/** Platform Account 的管理端投影。 */
export interface PlatformAccount {
  readonly avatarFileId: Int64String | null;
  readonly createdAt: string;
  readonly displayName: string;
  readonly email: null | string;
  readonly id: Int64String;
  readonly phoneNumber: null | string;
  readonly protectedRoot: boolean;
  readonly status: AccountStatus;
  readonly updatedAt: string;
  readonly username: string;
}

/** Controller 归一化后的 Platform Account 分页状态。 */
export interface AccountPage {
  readonly items: readonly PlatformAccount[];
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

/** 创建 Platform Account 的完整输入。 */
export interface CreateAccountInput {
  readonly displayName: string;
  readonly email?: null | string;
  readonly initialPassword: string;
  readonly phoneNumber?: null | string;
  readonly username: string;
}

/** Platform 角色状态。 */
export type RoleStatus = 'ACTIVE' | 'DISABLED';

/** 按 BCP 47 Locale 和字段名称保存的完整多语言字段。 */
export type LocalizedFields = Readonly<
  Record<string, Readonly<Record<string, string>>>
>;

/** 当前应用支持和默认使用的 Locale。 */
export interface LocaleCatalog {
  readonly defaultLocale: string;
  readonly supportedLocales: readonly string[];
}

/** SMTP 连接使用的传输安全模式。 */
export type SmtpSecurity = 'NONE' | 'STARTTLS' | 'TLS';

/** 管理端读取到的不含密码的 SMTP 配置。 */
export interface EmailSender {
  readonly enabled: boolean;
  readonly fromAddress: string;
  readonly fromName: null | string;
  readonly host: string;
  readonly name: string;
  readonly passwordConfigured: boolean;
  readonly port: number;
  readonly replyTo: null | string;
  readonly version: Int64String;
  readonly security: SmtpSecurity;
  readonly senderId: Int64String;
  readonly username: null | string;
}

/** 创建 SMTP 配置所需的连接、认证和发件资料。 */
export interface CreateEmailSenderInput {
  readonly fromAddress: string;
  readonly fromName: null | string;
  readonly host: string;
  readonly name: string;
  readonly password: null | string;
  readonly port: number;
  readonly replyTo: null | string;
  readonly security: SmtpSecurity;
  readonly username: null | string;
}

/** 完整替换 SMTP 配置时使用的资料和显式密码操作。 */
export interface ReplaceEmailSenderInput extends CreateEmailSenderInput {
  readonly passwordChanged: boolean;
}

/** 通知富文本中的 mark。 */
export interface NotificationRichTextMark {
  readonly attrs?: Readonly<Record<string, unknown>>;
  readonly type: string;
}

/** 通知模板使用的受限 ProseMirror JSON 节点。 */
export interface NotificationRichTextNode {
  readonly attrs?: Readonly<Record<string, unknown>>;
  readonly content?: readonly NotificationRichTextNode[];
  readonly marks?: readonly NotificationRichTextMark[];
  readonly text?: string;
  readonly type: string;
}

/** 一个 Locale 的平台无关通知标题和正文。 */
export interface NotificationTemplateLocaleContent {
  readonly body: NotificationRichTextNode;
  readonly title: NotificationRichTextNode;
}

/** Locale 到平台无关通知富文本的完整映射。 */
export type NotificationTemplateContent = Readonly<
  Record<string, NotificationTemplateLocaleContent>
>;

/** 已保存的多语言统一通知模板。 */
export interface EmailTemplate {
  readonly content: NotificationTemplateContent;
  readonly enabled: boolean;
  readonly notificationCode: string;
  readonly version: Int64String;
  readonly templateId: Int64String;
  readonly variables: readonly string[];
}

/** 创建或完整替换统一通知模板的输入。 */
export interface EmailTemplateInput {
  readonly content: NotificationTemplateContent;
  readonly notificationCode: string;
}

/** 使用未保存模板生成预览的输入。 */
export interface PreviewEmailTemplateInput {
  readonly content: NotificationTemplateContent;
  readonly languageTag: string;
  readonly variables: Readonly<Record<string, string>>;
}

/** 完成变量转义和 HTML 清洗后的 Email 内容。 */
export interface RenderedEmail {
  readonly html: null | string;
  readonly subject: string;
  readonly text: string;
}

/** 已保存的 SMS Provider 配置，不包含 AccessKey。 */
export interface SmsProvider {
  readonly code: string;
  readonly credentialRef: string;
  readonly enabled: boolean;
  readonly priority: number;
  readonly providerId: Int64String;
  readonly type: string;
  readonly version: Int64String;
}

/** 创建或完整替换 SMS Provider 的输入。 */
export interface SmsProviderInput {
  readonly code: string;
  readonly credentialRef: string;
  readonly priority: number;
  readonly type: string;
}

/** 统一通知到供应商模板的 SMS 传输绑定。 */
export interface SmsProviderBinding {
  readonly bindingId: Int64String;
  readonly enabled: boolean;
  readonly languageTag: string;
  readonly notificationCode: string;
  readonly parameterMapping: Readonly<Record<string, string>>;
  readonly providerId: Int64String;
  readonly providerTemplateCode: string;
  readonly route: string;
  readonly signName: string;
  readonly version: Int64String;
}

/** 创建或完整替换 SMS Provider 模板绑定的输入。 */
export interface SmsProviderBindingInput {
  readonly languageTag: string;
  readonly notificationCode: string;
  readonly parameterMapping: Readonly<Record<string, string>>;
  readonly providerTemplateCode: string;
  readonly route: string;
  readonly signName: string;
}

/** Platform 角色。 */
export interface PlatformRole {
  readonly builtIn: boolean;
  readonly code: string;
  readonly createdAt: string;
  readonly id: Int64String;
  readonly name: string;
  readonly status: RoleStatus;
  readonly updatedAt: string;
}

/** 聚合字典中可供一个业务输入选择的选项。 */
export interface DictionarySelectionOption {
  readonly codeDefined: boolean;
  readonly disabled: boolean;
  readonly label: string;
  readonly labelKey: null | string;
  readonly value: string;
}

/** 一个字典键及其已经按后端规则排序的聚合选择项。 */
export interface DictionarySelection {
  readonly key: string;
  readonly options: readonly DictionarySelectionOption[];
}

/** 字典管理页中的一个代码项或数据库维护项。 */
export interface ManagedDictionaryOption {
  readonly codeDefined: boolean;
  readonly enabled: boolean;
  readonly fallbackLabel: null | string;
  readonly i18n: LocalizedFields | null;
  readonly label: string;
  readonly labelKey: null | string;
  readonly sortOrder: number;
  readonly value: string;
  readonly version: Int64String | null;
}

/** 字典管理页中的一个代码字典或动态字典。 */
export interface ManagedDictionary {
  readonly codeDefined: boolean;
  readonly enabled: boolean;
  readonly key: string;
  readonly label: string;
  readonly options: readonly ManagedDictionaryOption[];
  readonly optionsEditable: boolean;
  readonly sortOrder: number;
  readonly version: Int64String | null;
}

/** 系统字典管理接口的完整读取结果。 */
export interface SystemDictionaries {
  readonly dictionaries: readonly ManagedDictionary[];
}

/** 创建动态字典时提交的稳定键、标签、状态和顺序。 */
export interface CreateDictionaryInput {
  readonly enabled: boolean;
  readonly key: string;
  readonly label: string;
  readonly sortOrder: number;
}

/** 修改动态字典时提交的标签、状态、顺序和乐观锁版本。 */
export interface UpdateDictionaryInput {
  readonly enabled: boolean;
  readonly label: string;
  readonly sortOrder: number;
  readonly version: Int64String;
}

/** 创建数据库字典项时提交的稳定值、回退标签、多语言标签、状态和顺序。 */
export interface CreateDictionaryOptionInput {
  readonly enabled: boolean;
  readonly i18n: LocalizedFields;
  readonly label: string;
  readonly sortOrder: number;
  readonly value: string;
}

/** 修改数据库字典项时提交的回退标签、多语言标签、状态、顺序和乐观锁版本。 */
export interface UpdateDictionaryOptionInput {
  readonly enabled: boolean;
  readonly i18n: LocalizedFields;
  readonly label: string;
  readonly sortOrder: number;
  readonly version: Int64String;
}

/** 设置管理页中的一个类型化设置。 */
export interface SystemSetting {
  readonly defaultValue: unknown;
  readonly description: string;
  readonly descriptionKey: string;
  readonly key: string;
  readonly label: string;
  readonly labelKey: string;
  readonly readOnly: boolean;
  readonly secret: boolean;
  readonly sortOrder: number;
  readonly stored: boolean;
  readonly value: unknown;
  readonly valueType: string;
  readonly version: Int64String;
}

/** 设置管理页中的一个展示分组。 */
export interface SystemSettingGroup {
  readonly key: string;
  readonly label: string;
  readonly labelKey: string;
  readonly settings: readonly SystemSetting[];
  readonly sortOrder: number;
}

/** 系统设置管理接口的完整读取结果。 */
export interface SystemSettings {
  readonly groups: readonly SystemSettingGroup[];
}

/** 当前 Principal 的一个完整个人偏好。 */
export interface Preference<T extends JsonValue = JsonValue> {
  readonly key: string;
  readonly schemaVersion: number;
  readonly stored: boolean;
  readonly value: T;
}

/** Platform 角色编辑使用的完整多语言字段。 */
export interface RoleI18n {
  readonly i18n: LocalizedFields;
  readonly roleId: Int64String;
}

/** Account 当前角色标识。 */
export interface AccountRoles {
  readonly accountId: Int64String;
  readonly roleIds: readonly Int64String[];
}

/** 角色权限。 */
export interface RolePermissions {
  readonly permissionCodes: readonly string[];
  readonly roleId: Int64String;
}

/** 权限目录节点。 */
export interface PermissionNode {
  readonly children: readonly PermissionNode[];
  readonly code: string;
  readonly grantable: boolean;
  readonly name: string;
  readonly nameKey: string;
  readonly parentCode: null | string;
  readonly sortOrder: number;
  readonly type: string;
}

/** 权限目录。 */
export interface PermissionCatalog {
  readonly items: readonly PermissionNode[];
  readonly total: number;
  readonly tree: readonly PermissionNode[];
}

/** 当前账号资料。 */
export interface Profile {
  readonly avatarFileId: Int64String | null;
  readonly displayName: string;
  readonly email: null | string;
  readonly phoneNumber: null | string;
  readonly principalId: Int64String;
  readonly updatedAt: string;
}

/** 申请文件上传后必须原样执行的短期请求。 */
export interface FileUpload {
  readonly expiresAt: string;
  readonly fileId: Int64String;
  readonly headers: Readonly<Record<string, string>>;
  readonly method: string;
  readonly url: string;
}

/** 已授权文件的短期读取地址。 */
export interface FileAccessUrl {
  readonly expiresAt: string;
  readonly fileId: Int64String;
  readonly url: string;
}

/** 当前账号密码凭据状态。 */
export interface PasswordStatus {
  readonly status: string;
  readonly updatedAt: string;
}

/** 当前账号已经激活的 TOTP 凭据。 */
export interface TotpCredential {
  readonly enabled: boolean;
}

/** 当前账号的短期 TOTP 配置会话。 */
export interface TotpEnrollment {
  readonly enrollmentId: string;
  readonly expiresAt: string;
}

/** 一个认证方法在登录和敏感动作中的用途开关。 */
export interface AuthenticationMethodSettings {
  readonly loginAdditionalEnabled: boolean;
  readonly loginStartEnabled: boolean;
  readonly methodCode: string;
  readonly sensitiveActionEnabled: boolean;
}

/** 当前账号登录和敏感动作使用的认证设置。 */
export interface AuthenticationSettings {
  readonly loginFactorCount: number;
  readonly methods: readonly AuthenticationMethodSettings[];
  readonly reusableAuthenticationMaximumAgeSeconds: Int64String;
  readonly sensitiveActionFactorCount: number;
}

/** 当前账号保存的 Passkey。 */
export interface PasskeyCredential {
  readonly backedUp: boolean;
  readonly backupEligible: boolean;
  readonly createdAt: string;
  readonly id: Int64String;
  readonly lastUsedAt: null | string;
  readonly name: string;
  readonly transports: readonly string[];
  readonly version: Int64String;
}

/** Passkey ceremony 的浏览器 JSON options。 */
export interface PasskeyOptions {
  readonly ceremonyId: string;
  readonly expiresAt: string;
  readonly optionsJson: string;
}

/** 浏览器完成 Passkey assertion 或 registration 后提交的 JSON。 */
export interface PasskeyProof {
  readonly ceremonyId: string;
  readonly responseJson: string;
}

/** 审计记录的执行结果。 */
export type AuditResult = 'DENIED' | 'FAILED' | 'SUCCESS';

/** 管理端展示的一条操作日志。 */
export interface OperationLog {
  readonly accountId: Int64String | null;
  readonly clientIp: null | string;
  readonly httpMethod: null | string;
  readonly httpPath: null | string;
  readonly id: Int64String;
  readonly occurredAt: string;
  readonly operation: string;
  readonly payloadJson: null | string;
  readonly payloadType: null | string;
  readonly payloadVersion: null | number;
  readonly principalDisplayName: null | string;
  readonly principalId: Int64String;
  readonly requestId: string;
  readonly result: AuditResult;
  readonly subjectId: Int64String;
  readonly targetId: null | string;
  readonly targetType: null | string;
  readonly userAgent: null | string;
}

/** Controller 归一化后的操作日志分页。 */
export interface OperationLogPage {
  readonly items: readonly OperationLog[];
  readonly page: number;
  readonly size: number;
  readonly total: number;
}
