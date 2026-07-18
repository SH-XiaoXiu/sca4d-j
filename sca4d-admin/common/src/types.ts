/** 后端 64 位主键的前端表示；始终使用字符串，避免 JavaScript 数字精度丢失。 */
export type Id = string;

/** 后端共用 HTTP 响应，包含业务码、本地化消息、追踪标识和实际数据。 */
export interface ApiResponse<T> {
  code: number;
  messageKey: string | null;
  messageArgs: unknown[];
  message: string | null;
  data: T;
  traceId: string | null;
  timestamp: string;
}

/** 后端分页接口共同使用的页码和每页数量。 */
export interface PageQuery {
  page?: number;
  size?: number;
}

/** 后端分页响应的原始结构；数字字段在 JSON 中保持字符串。 */
export interface PageData<T> {
  records: T[];
  page: string;
  size: string;
  total: string;
}

/** API 客户端把分页数字转换为 number 后返回给页面的结构。 */
export interface PageResult<T> {
  records: T[];
  page: number;
  size: number;
  total: number;
}

/** 游标分页接口的起始游标和每页数量。 */
export interface CursorQuery {
  cursor?: string;
  size?: number;
}

/** 游标分页响应，nextCursor 为空时表示没有下一页。 */
export interface CursorPageData<T> {
  records: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

/** 登录成功后返回的 Token 名称和值。 */
export interface SessionToken {
  tokenName: string;
  tokenValue: string;
}

/** 响应头解析出的客户端刷新要求和幂等重放标记。 */
export interface ClientStateChange {
  stale: boolean;
  refreshTargets: string[];
  version: string | null;
  idempotencyReplayed: boolean;
}

/** 文件签名项目执行上传或读取。 */
export type FileSignatureOperation = "UPLOAD" | "READ";
/** 文件字节由客户端直传对象存储或经后端代理传输。 */
export type FileUploadMode = "DIRECT" | "PROXY";
/** 当前文件上传协议固定使用的 HTTP 方法。 */
export type FileUploadMethod = "PUT";

/** 业务响应中指向已绑定文件的场景、资源和文件主键。 */
export interface FileSignatureRef {
  scene: string;
  resourceId: string;
  fileId: Id;
}

/** 单个文件上传或读取签名请求。 */
export interface FileSignatureItem {
  requestId: string;
  operation: FileSignatureOperation;
  scene: string;
  resourceId?: string;
  fileId?: Id;
  originalFilename?: string;
  contentType?: string;
  sizeBytes?: number;
}

/** 文件短期读取地址及其过期时间。 */
export interface FileAccessUrl {
  fileId: Id;
  url: string;
  expiresAt: string;
}

/** 文件上传地址、请求参数和完成上传后占用文件所需的授权。 */
export interface FileUpload {
  fileId: Id;
  uploadMode: FileUploadMode;
  method: FileUploadMethod;
  url: string;
  headers: Record<string, string>;
  formFields: Record<string, string>;
  expiresAt: string;
  uploadGrant: string;
}

/** 单个签名结果；读取返回 files，上传返回 upload。 */
export interface FileSignatureItemResult {
  requestId: string;
  operation: FileSignatureOperation;
  files?: FileAccessUrl[];
  upload?: FileUpload;
}

/** 保持请求顺序的批量文件签名结果。 */
export interface FileSignatureResult {
  items: FileSignatureItemResult[];
}

/** 查询参数允许的标量值。 */
export type QueryValue = string | number | boolean | Date | null | undefined;
/** 可以直接交给 HTTP 客户端序列化的查询参数集合。 */
export type QueryParameters = Record<string, QueryValue | QueryValue[]>;

/** 一次认证最终允许完成的操作。 */
export type AuthenticationPurpose = "LOGIN" | "REGISTRATION" | "LINKING" | "STEP_UP" | "RECOVERY";
/** 当前应用注册的认证方式类型。 */
export type AuthMethodType =
  | "PASSWORD"
  | "EMAIL_CODE"
  | "RECOVERY_CODE"
  | "TOTP"
  | "PASSKEY"
  | "OIDC"
  | "OAUTH2"
  | "LDAP";
/** 认证方式可以参与的认证目的。 */
export type AuthMethodCapability = "LOGIN" | "REGISTRATION" | "LINKING" | "STEP_UP" | "RECOVERY";
/** 登录入口可使用的认证方式及其允许的认证目的。 */
export interface AuthMethod {
  type: AuthMethodType;
  capabilities: AuthMethodCapability[];
}
/** Account 当前支持的登录标识类型。 */
export type AccountIdentifierType = "USERNAME" | "EMAIL";

/** 登录页选择的公开 Account 作用域；TENANT 使用业务编码，不包含数据库主键。 */
export type AuthenticationScopeSelection =
  | { scopeType: "GLOBAL" }
  | { scopeType: "TENANT"; tenantCode: string };

/** 创建短期认证的请求。 */
export interface AuthenticationCreateRequest {
  purpose: AuthenticationPurpose;
  scopeType?: AccountScopeType;
  tenantCode?: string;
  deviceId?: string;
  terminalName?: string;
}

/** 登录页可以选择的 ACTIVE OIDC Provider。 */
export interface OidcAvailableProvider {
  code: string;
  name: string;
}

/** 浏览器跳转到 OpenID Provider 使用的授权地址和过期时间。 */
export interface OidcAuthorization {
  authorizationUri: string;
  expiresAt: string;
}

/** 为当前认证选择 OIDC Provider 的请求。 */
export interface OidcAuthorizationCreateRequest {
  providerCode: string;
}

/** 登录页可以选择的 ACTIVE OAuth2 Provider。 */
export interface OAuth2AvailableProvider {
  code: string;
  name: string;
}

/** 浏览器跳转到 OAuth2 Provider 使用的授权地址和过期时间。 */
export interface OAuth2Authorization {
  authorizationUri: string;
  expiresAt: string;
}

/** 为当前认证选择 OAuth2 Provider 的请求。 */
export interface OAuth2AuthorizationCreateRequest {
  providerCode: string;
}

/** 登录页可以选择的 ACTIVE LDAP Provider。 */
export interface LdapAvailableProvider {
  code: string;
  name: string;
}

/** 向当前认证提交的 LDAP 用户凭据。 */
export interface LdapProofRequest {
  providerCode: string;
  username: string;
  password: string;
}

/** 向当前认证提交的登录标识和密码。 */
export interface PasswordProofRequest {
  identifierType?: AccountIdentifierType;
  identifier?: string;
  password: string;
}

/** 向 RECOVERY Flow 提交的 AccountIdentifier 和一次性恢复码。 */
export interface RecoveryCodeProofRequest {
  identifierType: AccountIdentifierType;
  identifier: string;
  recoveryCode: string;
}

/** 使用已验证 RECOVERY Flow 创建密码替换的请求。 */
export interface PasswordReplacementRequest {
  password: string;
}

/** 已投递邮件验证码 Challenge 的安全展示信息。 */
export interface EmailCodeChallenge {
  challengeId: string;
  maskedRecipient: string;
  expiresAt: string;
  resendAt: string;
}

/** 向当前认证提交的邮件验证码。 */
export interface EmailCodeProofRequest {
  challengeId: string;
  code: string;
}

/** 向当前认证提交的 TOTP 六位验证码。 */
export interface TotpProofRequest {
  code: string;
}

/** 创建后可交给 WebAuthn 客户端执行的 assertion options。 */
export interface PasskeyAssertionView {
  assertionId: string;
  expiresAt: string;
  options: unknown;
}

/** 向当前认证提交的一次性 assertion 和认证器响应。 */
export interface PasskeyProofRequest {
  assertionId: string;
  credential: unknown;
}

/** 当前认证正在等待的输入及已经确定的主体和账号。 */
export interface AuthenticationProcess {
  authenticationId: string;
  purpose: AuthenticationPurpose;
  activityId: "primary-proof" | "second-factor-proof" | "completion" | "completed";
  subjectId: Id | null;
  accountId: Id | null;
  expiresAt: string;
}

/** 登录成功后返回的 Token、Subject 和登录会话信息。 */
export interface LoginSession extends SessionToken {
  sessionId: string;
  subjectId: Id;
  accountId: Id;
  deviceId: string | null;
  terminalName: string | null;
  loginTime: string | null;
  expireTime: string | null;
}

/** Account 当前是否允许创建新的登录会话。 */
export type AccountStatus = "ACTIVE" | "DISABLED";
/** AccountIdentifier 当前是否可以参与登录解析。 */
export type AccountIdentifierStatus = "ACTIVE" | "DISABLED";
/** 当前已经实现并可在账号安全页展示的 Credential 类型。 */
export type CredentialType = "PASSWORD" | "TOTP" | "PASSKEY";
/** Credential 当前是否可以参与认证。 */
export type CredentialStatus = "ACTIVE" | "DISABLED";

/** 当前用户可见且不包含规范值和 Contact 主键的登录标识。 */
export interface AccountIdentifierView {
  identifierId: Id;
  identifierType: AccountIdentifierType;
  value: string;
  status: AccountIdentifierStatus;
  version: Id;
}

/** 当前用户可见且不包含摘要、密钥或协议秘密的 Credential。 */
export interface AccountCredentialView {
  credentialId: Id;
  credentialType: CredentialType;
  status: CredentialStatus;
  createdAt: string;
  updatedAt: string;
  version: Id;
}

/** Subject 名下一个 Account 的作用域和非敏感认证资料。 */
export interface AccountSecurityView {
  accountId: Id;
  scopeType: AccountScopeType;
  scopeId: Id | null;
  status: AccountStatus;
  secondFactorRequired: boolean;
  version: Id;
  recoveryCodesIssuedAt: string | null;
  recoveryCodesRemaining: number;
  identifiers: AccountIdentifierView[];
  credentials: AccountCredentialView[];
  passkeyCredentials: PasskeyCredentialView[];
}

/** 修改 Account 第二因子要求时提交的目标状态和当前版本。 */
export interface AccountSecondFactorRequest {
  required: boolean;
  version: Id;
}

/** 修改后的第二因子要求及仅本次响应展示的新恢复码。 */
export interface AccountSecondFactorView {
  required: boolean;
  accountVersion: Id;
  recoveryCodesIssuedAt: string | null;
  recoveryCodesRemaining: number;
  recoveryCodes: string[];
}

/** 不包含密钥和二维码内容的短期 TOTP enrollment。 */
export interface TotpEnrollmentView {
  enrollmentId: string;
  expiresAt: string;
}

/** 使用 enrollment 首次验证码创建 TOTP Credential 的请求。 */
export interface TotpCredentialCreateRequest {
  enrollmentId: string;
  code: string;
}

/** 使用 enrollment 首次验证码替换 TOTP Credential 的请求；版本通过 If-Match 提交。 */
export type TotpCredentialReplaceRequest = TotpCredentialCreateRequest;

/** TOTP Credential 创建或替换后的主键和版本。 */
export interface TotpCredentialChangeView {
  credentialId: Id;
  version: Id;
}

/** 创建 Passkey registration 时提交的账号安全页展示名称。 */
export interface PasskeyRegistrationCreateRequest {
  displayName: string;
}

/** 创建后可交给 WebAuthn 客户端执行的 registration options。 */
export interface PasskeyRegistrationView {
  registrationId: string;
  expiresAt: string;
  options: unknown;
}

/** 使用短期 registration 提交认证器 attestation。 */
export interface PasskeyCredentialCreateRequest {
  registrationId: string;
  credential: unknown;
}

/** 新增 Passkey 后返回的公共 Credential 主键和版本。 */
export interface PasskeyCredentialChangeView {
  credentialId: Id;
  version: Id;
}

/** 账号安全页可见且不包含 WebAuthn 公钥、credential ID 和 user handle 的 Passkey。 */
export interface PasskeyCredentialView {
  credentialId: Id;
  status: CredentialStatus;
  createdAt: string;
  updatedAt: string;
  version: Id;
  displayName: string;
  backupEligible: boolean;
  backupState: boolean;
  lastUsedAt: string | null;
}

/** 当前用户可见且不能用于认证的登录会话。 */
export interface AccountSessionView {
  sessionId: string;
  deviceId: string | null;
  terminalName: string | null;
  loginTime: string;
}

/** ExternalIdentity 对接的标准协议。 */
export type IdentityProviderProtocol = "OIDC" | "OAUTH2" | "LDAP";
/** IdentityProvider 是否可参与认证。 */
export type IdentityProviderStatus = "ACTIVE" | "DISABLED";
/** 未匹配本地账号时的注册策略。 */
export type IdentityProviderRegistrationPolicy = "DISABLED" | "JIT";

/** 当前 Account 可以关联的 ACTIVE IdentityProvider。 */
export interface AvailableIdentityProvider {
  code: string;
  name: string;
  protocol: IdentityProviderProtocol;
}

/** 管理端可见且不包含协议秘密的 IdentityProvider 通用字段。 */
export interface IdentityProviderView {
  providerId: Id;
  scopeType: AccountScopeType;
  scopeId: Id | null;
  code: string;
  name: string;
  protocol: IdentityProviderProtocol;
  registrationPolicy: IdentityProviderRegistrationPolicy;
  emailAutoLink: boolean;
  status: IdentityProviderStatus;
  version: Id;
}

/** 创建 IdentityProvider 时提交的稳定通用字段。 */
export interface IdentityProviderCreateInput {
  code: string;
  name: string;
  protocol: IdentityProviderProtocol;
  registrationPolicy: IdentityProviderRegistrationPolicy;
  emailAutoLink: boolean;
}

/** 修改 IdentityProvider 时提交的可变通用字段。 */
export interface IdentityProviderUpdateInput {
  name: string;
  registrationPolicy: IdentityProviderRegistrationPolicy;
  emailAutoLink: boolean;
}

/** 不包含 Client Secret 的 OIDC 配置。 */
export interface OidcProviderConfigurationView {
  providerId: Id;
  issuer: string;
  clientId: string;
  clientSecretConfigured: boolean;
  scopes: string[];
  version: Id;
}

/** 创建或完整替换 OIDC 配置的输入。 */
export interface OidcProviderConfigurationInput {
  issuer: string;
  clientId: string;
  clientSecret?: string;
  scopes: string[];
}

/** OIDC Discovery 预检返回的标准端点和能力。 */
export interface OidcDiscoveryView {
  providerId: Id;
  issuer: string;
  authorizationUri: string;
  tokenUri: string;
  userInfoUri: string | null;
  jwksUri: string;
  scopesSupported: string[];
  responseTypesSupported: string[];
  subjectTypesSupported: string[];
  idTokenSigningAlgorithmsSupported: string[];
  tokenEndpointAuthenticationMethodsSupported: string[];
  codeChallengeMethodsSupported: string[];
}

/** OAuth2 Profile 到标准外部身份字段的只读 JSONPath 映射。 */
export interface OAuth2ProfileMapping {
  subjectPath: string;
  displayNamePath?: string;
  emailPath?: string;
  emailVerifiedPath?: string;
  avatarPath?: string;
}

/** 不包含 Client Secret 的标准 OAuth2 配置。 */
export interface OAuth2ProviderConfigurationView {
  providerId: Id;
  authorizationUri: string;
  tokenUri: string;
  profileUri: string;
  auxiliaryProfileUri: string | null;
  clientId: string;
  clientSecretConfigured: boolean;
  scopes: string[];
  profileMapping: OAuth2ProfileMapping;
  version: Id;
}

/** 创建或完整替换标准 OAuth2 配置的输入。 */
export interface OAuth2ProviderConfigurationInput {
  authorizationUri: string;
  tokenUri: string;
  profileUri: string;
  auxiliaryProfileUri?: string;
  clientId: string;
  clientSecret?: string;
  scopes: string[];
  profileMapping: OAuth2ProfileMapping;
}

/** OAuth2 Profile 映射中缺失的标准字段。 */
export interface OAuth2ProfileField {
  value: string;
  description: string;
}

/** OAuth2 示例 Profile 映射预览。 */
export interface OAuth2ProfilePreviewView {
  subject: string | null;
  displayName: string | null;
  email: string | null;
  emailVerified: boolean | null;
  avatarUri: string | null;
  missingFields: OAuth2ProfileField[];
}

/** LDAP 连接的 TLS 方式。 */
export type LdapTransportSecurity = "STARTTLS" | "LDAPS";

/** 不包含服务账号密码的 LDAP 配置。 */
export interface LdapProviderConfigurationView {
  providerId: Id;
  url: string;
  baseDn: string;
  serviceAccountDn: string | null;
  serviceAccountPasswordConfigured: boolean;
  userSearchBase: string;
  userSearchFilter: string;
  externalSubjectAttribute: string;
  displayNameAttribute: string | null;
  emailAttribute: string | null;
  transportSecurity: LdapTransportSecurity;
  version: Id;
}

/** 创建或完整替换 LDAP 配置的输入。 */
export interface LdapProviderConfigurationInput {
  url: string;
  baseDn: string;
  serviceAccountDn?: string;
  serviceAccountPassword?: string;
  userSearchBase: string;
  userSearchFilter: string;
  externalSubjectAttribute: string;
  displayNameAttribute?: string;
  emailAttribute?: string;
  transportSecurity: LdapTransportSecurity;
}

/** 当前 Account 可见的 ExternalIdentity 绑定。 */
export interface ExternalIdentityView {
  identityId: Id;
  providerId: Id;
  providerCode: string;
  providerName: string;
  protocol: IdentityProviderProtocol;
  providerStatus: IdentityProviderStatus;
  externalSubject: string;
  displayName: string | null;
  email: string | null;
  emailVerified: boolean;
  avatarUri: string | null;
  lastAuthenticatedAt: string | null;
  version: Id;
}

/** 当前登录自然人的主键和展示名称。 */
export interface SubjectView {
  subjectId: Id;
  displayName: string | null;
}

/** 本次 Session 固定使用的登录账号和身份作用域。 */
export interface AccountView {
  accountId: Id;
  scopeType: AccountScopeType;
  scopeId: Id | null;
}

/** 当前请求所选 Principal 的类型、租户和展示名称。 */
export interface PrincipalView {
  principalId: Id;
  principalType: PrincipalType;
  tenantId: Id | null;
  displayName: string | null;
}

/** 当前登录设备和 Session 时间信息，不包含 Token 值。 */
export interface SessionView {
  tokenName: string;
  deviceId: string | null;
  terminalName: string | null;
  loginTime: string | null;
  expireTime: string | null;
}

/** 当前登录用户的 Subject、Principal、权限、租户和导航快照。 */
export interface MeView {
  subject: SubjectView | null;
  account: AccountView;
  avatar: FileSignatureRef | null;
  profileVersion: Id;
  principal: PrincipalView | null;
  session: SessionView | null;
  roles: string[];
  permissions: string[];
  availablePrincipals: AvailablePrincipalView[];
  tenants: TenantMembership[];
  navigation: NavigationMenuView[];
}

/** 请求可以选择的 Principal 类型。 */
export type PrincipalType = "SYSTEM" | "DEFAULT" | "PLATFORM" | "TENANT";
/** Account 登录标识的解析作用域。 */
export type AccountScopeType = "GLOBAL" | "TENANT";
/** Principal 当前是否可以被请求选择。 */
export type PrincipalStatus = "ACTIVE" | "DISABLED";
/** Membership 当前是否允许 Subject 进入租户。 */
export type MembershipStatus = "ACTIVE" | "DISABLED";
/** 角色当前是否参与权限计算。 */
export type RoleStatus = "ACTIVE" | "DISABLED";
/** 租户当前是否允许成员访问。 */
export type TenantStatus = "ACTIVE" | "DISABLED";
/** 当前租户对象支持的租户类型。 */
export type TenantType = "DEFAULT";
/** 权限目录中的模块、资源和可授权动作类型。 */
export type PermissionType = "MODULE" | "RESOURCE" | "ACTION";
/** 代码枚举、业务固定值和输入建议三种字典用途。 */
export type DictionaryKind = "CODE_ENUM" | "BUSINESS_CODE" | "INPUT_SUGGESTION";
/** 字典由代码同步还是由管理端创建。 */
export type DictionarySource = "CODE" | "CONFIGURATION";
/** 字典或字典项当前是否允许用于新输入。 */
export type DictionaryStatus = "ACTIVE" | "DISABLED";
/** 管理端展示字典项时使用的颜色含义。 */
export type DictionaryTone = "DEFAULT" | "INFO" | "SUCCESS" | "WARNING" | "DANGER";
/** 租户 ACL 允许的读取、写入和管理等级。 */
export type AccessAction = "READ" | "WRITE" | "MANAGE";
/** 菜单使用的 Lucide 图标名称。 */
export type MenuIcon = string;
/** 一次执行和 Cron 重复执行两种 Schedule 类型。 */
export type ScheduleType = "ONCE" | "CRON";
/** Schedule 当前运行、暂停、完成、失败或删除状态。 */
export type ScheduleStatus = "ACTIVE" | "PAUSED" | "COMPLETED" | "FAILED" | "CANCELLED";
/** 单次 Schedule 执行记录的处理状态。 */
export type ScheduleExecutionStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "SKIPPED";
/** 单次执行由计划、人工请求或恢复任务触发。 */
export type ScheduleTriggerSource = "SCHEDULED" | "MANUAL" | "RECOVERY";
/** 业务错误分类及其对应的 HTTP 响应类型。 */
export type ResponseCategory =
  | "SUCCESS"
  | "BAD_REQUEST"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "METHOD_NOT_ALLOWED"
  | "CONFLICT"
  | "PAYLOAD_TOO_LARGE"
  | "UNSUPPORTED_MEDIA_TYPE"
  | "TOO_MANY_REQUESTS"
  | "EXTERNAL_SERVICE_ERROR"
  | "INTERNAL_SERVICE_ERROR"
  | "SERVER_ERROR";

/** 当前 Subject 可以选择的一个 Principal。 */
export interface AvailablePrincipalView {
  principalId: Id;
  principalType: PrincipalType;
  tenantId: Id | null;
  displayName: string | null;
  status: PrincipalStatus;
}

/** 当前 Subject 在一个租户中的成员 Principal 和租户状态。 */
export interface TenantMembership {
  membershipId: Id;
  principalId: Id;
  tenantId: Id;
  tenantCode: string;
  tenantName: string;
  tenantType: TenantType;
  tenantStatus: TenantStatus;
  membershipStatus: MembershipStatus;
  version: Id;
}

/** 操作日志等页面使用的资源名称、可导航状态和详情地址。 */
export interface ResourceReferenceView {
  resourceType: string;
  resourceId: Id;
  label: string | null;
  tenantId: Id | null;
  navigationState: "NAVIGABLE" | "FORBIDDEN" | "DELETED" | "UNSUPPORTED";
  path: string | null;
  navigationMessage: string | null;
}

/** PLATFORM Principal 对一个具体租户的访问等级。 */
export interface TenantAccessRuleView {
  tenant: ResourceReferenceView;
  accessAction: AccessAction;
}

/** PLATFORM Principal 的默认租户访问等级和例外规则。 */
export interface PlatformTenantAccessView {
  principalId: Id;
  version: Id;
  defaultAction: AccessAction | null;
  grants: TenantAccessRuleView[];
}

/** 当前 Principal 可以访问的递归导航菜单。 */
export interface NavigationMenuView {
  menuId: Id;
  routeCode: string | null;
  path: string | null;
  title: string;
  icon: MenuIcon;
  children: NavigationMenuView[];
}

/** 后端支持的一种语言及其默认和必填标记。 */
export interface LanguageView {
  code: string;
  nativeName: string;
  defaultLanguage: boolean;
  required: boolean;
}

/** 菜单管理页面读取的完整菜单节点。 */
export interface ManagedMenuView {
  menuId: Id;
  parentId: Id | null;
  routeCode: string | null;
  pathTemplate: string | null;
  permissionCode: string | null;
  titles: Record<string, string>;
  icon: MenuIcon;
  sortOrder: number;
  visible: boolean;
  version: Id;
  children: ManagedMenuView[];
}

/** 前端已注册路由及其允许的 Principal、权限和默认标题。 */
export interface NavigationRouteView {
  code: string;
  pathTemplate: string;
  principalTypes: PrincipalType[];
  permissionCode: string | null;
  defaultTitles: Record<string, string>;
  defaultIcon: MenuIcon;
}

/** 创建或修改菜单时提交的父节点、路由、标题、图标和显隐状态。 */
export interface MenuWriteInput {
  parentId?: Id;
  routeCode?: string;
  titles: Record<string, string>;
  icon: MenuIcon;
  visible: boolean;
}

/** 通知 Schedule 固定保存的 DEFAULT Principal 接收人。 */
export interface NotificationScheduleRecipient {
  subjectId: Id;
  principalType: "DEFAULT";
  principalId: Id;
}

/** 通知 ScheduleTask 创建站内通知所需的标题、正文和接收人。 */
export interface NotificationSchedulePayload {
  title: string;
  content: string;
  category: "SYSTEM";
  recipients: NotificationScheduleRecipient[];
}

/** Schedule 定义、当前状态、同步错误和乐观锁版本。 */
export interface ScheduleView {
  scheduleId: Id;
  name: string;
  scheduleType: ScheduleType;
  executeAt: string | null;
  cronExpression: string | null;
  timeZone: string | null;
  status: ScheduleStatus;
  taskType: string;
  taskPayload: NotificationSchedulePayload;
  taskPayloadVersion: number;
  synchronizationError: string | null;
  createdByPrincipalId: Id;
  version: Id;
  createdAt: string;
  updatedAt: string;
}

/** 单次 Schedule 执行的触发时间、结果和安全错误摘要。 */
export interface ScheduleExecutionView {
  executionId: Id;
  scheduleId: Id;
  triggerSource: ScheduleTriggerSource;
  scheduledFireTime: string | null;
  actualFireTime: string | null;
  completedAt: string | null;
  status: ScheduleExecutionStatus;
  taskType: string;
  resultSummary: string | null;
  safeErrorType: string | null;
  safeErrorMessage: string | null;
  executedInstanceId: string | null;
  createdAt: string;
}

/** 后端已注册的 ScheduleTask 类型和当前内容版本。 */
export interface ScheduleTaskTypeView {
  taskType: string;
  payloadVersion: number;
  displayName: string;
}

/** 创建或完整替换 Schedule 时提交的计划和任务内容。 */
export interface ScheduleWriteInput {
  name: string;
  scheduleType: ScheduleType;
  executeAt?: string;
  cronExpression?: string;
  timeZone?: string;
  taskType: string;
  taskPayload: NotificationSchedulePayload;
}

/** Schedule 分页接口支持的关键词、类型、状态和任务筛选。 */
export interface SchedulePageQuery extends PageQuery, QueryParameters {
  keyword?: string;
  scheduleType?: ScheduleType;
  status?: ScheduleStatus;
  taskType?: string;
}

/** 只读展示系统 Quartz Job 的 Trigger 和最近运行结果。 */
export interface SystemScheduledJobView {
  jobGroup: string;
  jobName: string;
  description: string;
  triggerState: "NORMAL" | "PAUSED" | "BLOCKED" | "ERROR" | "NONE";
  scheduleType: "INTERVAL" | "CRON";
  interval: string | null;
  cronExpression: string | null;
  previousFireTime: string | null;
  nextFireTime: string | null;
  lastActualFireTime: string | null;
  lastResult: "SUCCESS" | "FAILED" | null;
  lastDuration: string | null;
  safeErrorSummary: string | null;
  executedInstanceId: string | null;
  manageable: boolean;
}

/** SMTP 连接建立时使用的传输安全方式。 */
export type SmtpSecurity = "NONE" | "STARTTLS" | "IMPLICIT_TLS";
/** 邮件发送配置是否可以承担业务邮件投递。 */
export type MailSenderStatus = "ACTIVE" | "DISABLED";

/** 不包含 SMTP 密码或任何加密列的邮件发送配置。 */
export interface MailSenderView {
  senderId: Id;
  name: string;
  host: string;
  port: number;
  security: SmtpSecurity;
  username: string | null;
  passwordConfigured: boolean;
  fromAddress: string;
  fromName: string | null;
  replyTo: string | null;
  status: MailSenderStatus;
  version: Id;
}

/** 创建或完整修改 SMTP 地址、认证信息和默认发件资料。 */
export interface MailSenderWriteInput {
  name: string;
  host: string;
  port: number;
  security: SmtpSecurity;
  username?: string;
  password?: string;
  fromAddress: string;
  fromName?: string;
  replyTo?: string;
}

/** 邮件模板是否可以用于业务邮件发送。 */
export type MailTemplateStatus = "ACTIVE" | "DISABLED";

/** 一个 family 和 locale 下的邮件主题、文本、HTML 及变量目录。 */
export interface MailTemplateView {
  templateId: Id;
  family: string;
  locale: string;
  subjectTemplate: string;
  textTemplate: string;
  htmlTemplate: string | null;
  variableNames: string[];
  status: MailTemplateStatus;
  version: Id;
}

/** 创建或完整修改邮件模板时提交的主题、正文、HTML 和语言。 */
export interface MailTemplateWriteInput {
  family: string;
  locale: string;
  subjectTemplate: string;
  textTemplate: string;
  htmlTemplate?: string;
}

/** 受限 Mustache 模板使用的简单字符串变量。 */
export type MailTemplateVariables = Record<string, string>;

/** 已完成变量替换和 HTML 清洗的管理端预览。 */
export interface RenderedMailView {
  subject: string;
  text: string;
  html: string | null;
}

/** 请求当前用户快照时指定 Principal 类型和租户的选择参数。 */
export interface MeSelectionQuery extends QueryParameters {
  principalType?: Exclude<PrincipalType, "SYSTEM">;
  tenantId?: Id;
}

/** PLATFORM Principal 已绑定的平台角色摘要。 */
export interface PlatformRoleView {
  roleId: Id;
  code: string;
  name: string;
}

/** 管理端的 Account 摘要，不包含角色、认证秘密或 Subject 管理资料。 */
export interface ManagedAccountView {
  accountId: Id;
  subjectId: Id;
  defaultPrincipalId: Id;
  displayName: string;
  primaryIdentifier: string | null;
  avatar: FileSignatureRef | null;
  subjectVersion: Id;
  profileVersion: Id;
  scopeType: AccountScopeType;
  scopeId: Id | null;
  status: AccountStatus;
  secondFactorRequired: boolean;
  version: Id;
  createdAt: string;
  updatedAt: string;
}

/** GLOBAL Account 列表支持的状态筛选。 */
export interface AccountPageQuery extends PageQuery, QueryParameters {
  search?: string;
  status?: AccountStatus;
}

/** GLOBAL Account 对应 Subject 的通用资料。 */
export interface ManagedAccountProfileView {
  accountId: Id;
  subjectId: Id;
  displayName: string;
  phone: string | null;
  email: string | null;
  avatar: FileSignatureRef | null;
  subjectVersion: Id;
  profileVersion: Id;
}

/** 修改 GLOBAL Account 通用资料时提交的字段。 */
export interface ManagedAccountProfileUpdateInput {
  displayName: string;
  phone?: string;
  email?: string;
  subjectVersion: Id;
}

/** 创建 GLOBAL Account 时提交的首个登录标识、密码和默认展示资料。 */
export interface GlobalAccountCreateInput {
  identifier: string;
  password: string;
  displayName: string;
  phone?: string;
  email?: string;
  idempotencyKey: string;
}

/** 平台操作员及其角色，不包含认证材料或 Account 安全状态。 */
export interface PlatformOperatorView {
  principalId: Id;
  displayName: string;
  status: PrincipalStatus;
  version: Id;
  roles: PlatformRoleView[];
}

/** 平台操作员列表的分页参数。 */
export interface PlatformOperatorPageQuery extends PageQuery, QueryParameters {
}

/** 从已有 GLOBAL Account 创建独立 PLATFORM Principal 的请求。 */
export interface PlatformOperatorCreateInput {
  accountId: Id;
  displayName: string;
  idempotencyKey: string;
}

/** 租户开放编码、名称、状态和乐观锁版本。 */
export interface TenantView {
  tenantId: Id;
  code: string;
  name: string;
  tenantType: TenantType;
  status: TenantStatus;
  createdAt: string;
  updatedAt: string;
  version: Id;
}

/** 租户分页接口支持的关键词和状态筛选。 */
export interface TenantPageQuery extends PageQuery, QueryParameters {
  search?: string;
  status?: TenantStatus;
}

/** 创建租户时提交的开放编码、名称和幂等键。 */
export interface TenantCreateInput {
  code: string;
  name: string;
  tenantType: TenantType;
  idempotencyKey: string;
}

/** 字典管理页面读取的字典类型和当前语言标签。 */
export interface ManagedDictionaryView {
  id: Id;
  tenantId: Id | null;
  code: string;
  kind: DictionaryKind;
  source: DictionarySource;
  labelKey: string | null;
  labels: Record<string, string>;
  label: string;
  status: DictionaryStatus;
  sortOrder: number;
  version: Id;
  createdAt: string;
  updatedAt: string;
}

/** 字典管理页面读取的字典项、展示样式和状态。 */
export interface ManagedDictionaryItemView {
  id: Id;
  dictionaryId: Id;
  value: string;
  source: DictionarySource;
  labelKey: string | null;
  labels: Record<string, string>;
  label: string;
  tone: DictionaryTone;
  selectable: boolean;
  status: DictionaryStatus;
  sortOrder: number;
  version: Id;
  createdAt: string;
  updatedAt: string;
}

/** 字典类型分页接口支持的关键词、用途和状态筛选。 */
export interface DictionaryPageQuery extends PageQuery, QueryParameters {
  search?: string;
  kind?: DictionaryKind;
  status?: DictionaryStatus;
}

/** 字典项分页接口支持的关键词和状态筛选。 */
export interface DictionaryItemPageQuery extends PageQuery, QueryParameters {
  search?: string;
  status?: DictionaryStatus;
}

/** 创建非代码枚举字典时提交的编码、多语言标签和幂等键。 */
export interface DictionaryCreateInput {
  code: string;
  kind: Exclude<DictionaryKind, "CODE_ENUM">;
  labels: Record<string, string>;
  idempotencyKey: string;
}

/** 修改字典多语言标签和启用状态时提交的数据。 */
export interface DictionaryUpdateInput {
  labels: Record<string, string>;
  status: DictionaryStatus;
  version: Id;
}

/** 创建字典项时提交的固定值、标签、样式和幂等键。 */
export interface DictionaryItemCreateInput {
  value: string;
  labels: Record<string, string>;
  tone: DictionaryTone;
  selectable: boolean;
  idempotencyKey: string;
}

/** 拖动排序时使用的字典或字典项主键、名称和版本。 */
export interface DictionaryOrderItemView {
  id: Id;
  label: string;
  description: string;
  version: Id;
}

/** 修改字典项内容、样式、状态和版本时提交的数据。 */
export interface DictionaryItemUpdateInput {
  value: string;
  labels: Record<string, string>;
  tone: DictionaryTone;
  selectable: boolean;
  status: DictionaryStatus;
  version: Id;
}

/** 业务表单使用的一个本地化字典选项。 */
export interface DictionaryOptionView {
  value: string;
  labelKey: string | null;
  label: string;
  tone: DictionaryTone;
  disabled: boolean;
}

/** 业务表单按字典编码读取的选项集合。 */
export interface DictionarySelectionView {
  code: string;
  kind: DictionaryKind;
  labelKey: string | null;
  label: string;
  options: DictionaryOptionView[];
}

/** 租户成员当前绑定的一个角色。 */
export interface RoleAssignment {
  principalId: Id;
  roleId: Id;
  roleCode: string;
  roleName: string;
}

/** 角色详情中展示的一个权限目录节点。 */
export interface ManagedRolePermissionView {
  permissionId: Id;
  parentId: Id | null;
  code: string;
  nameKey: string;
  name: string;
  permissionType: PermissionType;
  sortOrder: number;
}

/** 角色资料、使用数量、权限集合和乐观锁版本。 */
export interface ManagedRoleView {
  roleId: Id;
  tenantId: Id | null;
  code: string;
  name: string;
  status: RoleStatus;
  builtIn: boolean;
  principalCount: number;
  permissionCount: number;
  permissions: ManagedRolePermissionView[];
  createdAt: string;
  updatedAt: string;
  version: Id;
}

/** 删除角色前查询的 Principal 使用数量。 */
export interface RoleUsageView {
  roleId: Id;
  principalCount: number;
}

/** 角色分页接口支持的关键词和状态筛选。 */
export interface RolePageQuery extends PageQuery, QueryParameters {
  search?: string;
  status?: RoleStatus;
}

/** 创建角色时提交的编码、名称、权限主键和幂等键。 */
export interface RoleCreateInput {
  code: string;
  name: string;
  permissionIds: Id[];
  idempotencyKey: string;
}

/** 修改角色名称和启用状态时提交的版本。 */
export interface RoleUpdateInput {
  name: string;
  status: RoleStatus;
  version: Id;
}

/** 租户成员选择器使用的角色摘要。 */
export interface TenantRoleView {
  roleId: Id;
  code: string;
  name: string;
}

/** 租户成员资料、状态、头像、角色和乐观锁版本。 */
export interface TenantMemberView {
  membershipId: Id;
  principalId: Id;
  subjectId: Id;
  tenantId: Id;
  displayName: string;
  membershipStatus: MembershipStatus;
  membershipVersion: Id;
  principalStatus: PrincipalStatus;
  principalVersion: Id;
  roles: RoleAssignment[];
}

/** 租户成员分页接口支持的关键词和状态筛选。 */
export interface TenantMemberPageQuery extends PageQuery, QueryParameters {
  search?: string;
  status?: MembershipStatus;
}

/** 创建租户 Account 并加入成员时提交的认证资料和初始角色。 */
export interface TenantMemberCreateAccountInput {
  identifier: string;
  password: string;
  displayName: string;
  phone?: string;
  email?: string;
  roleIds: Id[];
  idempotencyKey: string;
}

/** 使用既有 Account 创建 TENANT Principal 时提交的成员资料和初始角色。 */
export interface TenantMemberExistingAccountInput {
  accountId: Id;
  displayName: string;
  roleIds: Id[];
  idempotencyKey: string;
}

/** 权限树中的递归节点。 */
export interface PermissionNodeView {
  code: string;
  parentCode: string | null;
  name: string;
  type: PermissionType;
  grantable: boolean;
  sortOrder: number;
  children: PermissionNodeView[];
}

/** 权限选择器使用的扁平节点。 */
export interface PermissionItemView {
  code: string;
  parentCode: string | null;
  name: string;
  type: PermissionType;
  grantable: boolean;
  sortOrder: number;
}

/** 同时包含树形和扁平权限列表的目录响应。 */
export interface PermissionCatalogView {
  tree: PermissionNodeView[];
  items: PermissionItemView[];
  total: number;
}

/** 一个业务错误码的消息、分类、HTTP 状态和来源。 */
export interface ErrorCodeView {
  code: number;
  messageKey: string;
  message: string;
  category: ResponseCategory;
  httpStatus: number;
  source: string;
}

/** 一个模块声明的错误码数字区间。 */
export interface CodeRangeView {
  source: string;
  start: number;
  end: number;
  size: number;
}

/** 错误码条目和模块号段的完整目录。 */
export interface ErrorCodeCatalogView {
  items: ErrorCodeView[];
  ranges: CodeRangeView[];
  total: number;
}
