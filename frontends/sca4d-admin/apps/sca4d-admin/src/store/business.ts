import type {
  AccountDeletionOutcome,
  AccountFilters,
  AccountsState,
  AccountStatus,
  CreateAccountInput,
  CreateDictionaryInput,
  CreateDictionaryOptionInput,
  CreateEmailSenderInput,
  CreateFederatedProviderInput,
  CreateIotProductInput,
  EmailSender,
  EmailSendersState,
  EmailTemplate,
  EmailTemplateInput,
  EmailTemplatesState,
  FederatedProvider,
  FederatedProviderSettings,
  FederatedProviderSettingsInput,
  FederatedProvidersState,
  Int64String,
  IotDatabaseProductCatalogItem,
  IotProductsState,
  IotProductStatus,
  LocalizedFields,
  ManagedDictionary,
  ManagedDictionaryOption,
  OperationLogFilters,
  OperationLogsState,
  PermissionsState,
  PlatformAccount,
  PlatformRole,
  PreviewEmailTemplateInput,
  ProfileState,
  PublishIotThingModelInput,
  RenderedEmail,
  ReplaceEmailSenderInput,
  ReplaceFederatedProviderInput,
  RolesState,
  RoleStatus,
  SmsNotificationsState,
  SmsProvider,
  SmsProviderBinding,
  SmsProviderBindingInput,
  SmsProviderInput,
  SystemDictionariesState,
  SystemSetting,
  SystemSettingsState,
  UpdateDictionaryInput,
  UpdateDictionaryOptionInput,
  UpdateIotProductInput,
} from '@sca4d/admin';

import { computed, shallowRef } from 'vue';

import { useAccessStore, useUserStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { browserPasskeyClient } from '#/auth/passkey';
import { businessApplication } from '#/platform/business-application';
import { useAuthStore } from '#/store/auth';

/** 订阅所有纯 TypeScript Controller，并向 Vue 页面转发单个业务动作。 */
export const useAdminStore = defineStore('admin', () => {
  const accessStore = useAccessStore();
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const accounts = shallowRef<Readonly<AccountsState>>(
    businessApplication.accounts.state,
  );
  const roles = shallowRef<Readonly<RolesState>>(
    businessApplication.roles.state,
  );
  const permissions = shallowRef<Readonly<PermissionsState>>(
    businessApplication.permissions.state,
  );
  const operationLogs = shallowRef<Readonly<OperationLogsState>>(
    businessApplication.operationLogs.state,
  );
  const emailSenders = shallowRef<Readonly<EmailSendersState>>(
    businessApplication.emailSenders.state,
  );
  const emailTemplates = shallowRef<Readonly<EmailTemplatesState>>(
    businessApplication.emailTemplates.state,
  );
  const smsNotifications = shallowRef<Readonly<SmsNotificationsState>>(
    businessApplication.smsNotifications.state,
  );
  const profile = shallowRef<Readonly<ProfileState>>(
    businessApplication.profile.state,
  );
  const systemDictionaries = shallowRef<Readonly<SystemDictionariesState>>(
    businessApplication.systemDictionaries.state,
  );
  const systemSettings = shallowRef<Readonly<SystemSettingsState>>(
    businessApplication.systemSettings.state,
  );
  const federatedProviders = shallowRef<Readonly<FederatedProvidersState>>(
    businessApplication.federatedProviders.state,
  );
  const iotProducts = shallowRef<Readonly<IotProductsState>>(
    businessApplication.iotProducts.state,
  );
  const session = computed(() => {
    const codes = new Set(accessStore.accessCodes);
    return {
      capabilities: {
        accountsManage: codes.has('platform:accounts:manage'),
        accountsRead:
          codes.has('platform:accounts:read') ||
          codes.has('platform:accounts:manage'),
        rolesManage: codes.has('platform:roles:manage'),
        rolesRead:
          codes.has('platform:roles:read') ||
          codes.has('platform:roles:manage'),
        auditRead: codes.has('system:audit:read'),
        dictionariesManage: codes.has('system:dictionaries:manage'),
        dictionariesRead:
          codes.has('system:dictionaries:read') ||
          codes.has('system:dictionaries:manage'),
        emailManage: codes.has('system:email:manage'),
        emailRead:
          codes.has('system:email:read') || codes.has('system:email:manage'),
        smsManage: codes.has('system:sms:manage'),
        smsRead: codes.has('system:sms:read') || codes.has('system:sms:manage'),
        settingsManage: codes.has('system:settings:manage'),
        settingsRead:
          codes.has('system:settings:read') ||
          codes.has('system:settings:manage'),
        federatedProvidersManage: codes.has('platform:accounts:manage'),
        federatedProvidersRead:
          codes.has('platform:accounts:read') ||
          codes.has('platform:accounts:manage'),
        iotProductsManage: codes.has('iot:products:manage'),
        iotProductsRead:
          codes.has('iot:products:read') || codes.has('iot:products:manage'),
      },
      session: authStore.currentSession,
    };
  });

  businessApplication.accounts.subscribe((value) => (accounts.value = value));
  businessApplication.roles.subscribe((value) => (roles.value = value));
  businessApplication.permissions.subscribe(
    (value) => (permissions.value = value),
  );
  businessApplication.operationLogs.subscribe(
    (value) => (operationLogs.value = value),
  );
  businessApplication.emailSenders.subscribe(
    (value) => (emailSenders.value = value),
  );
  businessApplication.emailTemplates.subscribe(
    (value) => (emailTemplates.value = value),
  );
  businessApplication.smsNotifications.subscribe(
    (value) => (smsNotifications.value = value),
  );
  businessApplication.profile.subscribe((value) => (profile.value = value));
  businessApplication.systemDictionaries.subscribe(
    (value) => (systemDictionaries.value = value),
  );
  businessApplication.systemSettings.subscribe(
    (value) => (systemSettings.value = value),
  );
  businessApplication.federatedProviders.subscribe(
    (value) => (federatedProviders.value = value),
  );
  businessApplication.iotProducts.subscribe(
    (value) => (iotProducts.value = value),
  );

  /** 读取指定页的双来源 IoT Product 目录和表单选项；未传分页时沿用当前页。 */
  async function loadIotProducts(page?: number, size?: number): Promise<void> {
    await businessApplication.iotProducts.load(page, size);
  }

  /** 创建默认停用的数据库 IoT Product。 */
  async function createIotProduct(input: CreateIotProductInput): Promise<void> {
    await businessApplication.iotProducts.create(input);
  }

  /** 修改停用数据库 IoT Product 的技术定义。 */
  async function updateIotProduct(
    product: IotDatabaseProductCatalogItem,
    input: UpdateIotProductInput,
  ): Promise<void> {
    await businessApplication.iotProducts.update(product, input);
  }

  /** 修改数据库 IoT Product 的启停状态。 */
  async function changeIotProductStatus(
    product: IotDatabaseProductCatalogItem,
    status: IotProductStatus,
  ): Promise<void> {
    await businessApplication.iotProducts.changeStatus(product, status);
  }

  /** 读取任意来源 IoT Product 的全部物模型版本。 */
  async function loadIotThingModels(productCode: string): Promise<void> {
    await businessApplication.iotProducts.loadThingModels(productCode);
  }

  /** 读取任意来源 IoT Product 的一个物模型版本。 */
  async function loadIotThingModel(
    productCode: string,
    version: number,
  ): Promise<void> {
    await businessApplication.iotProducts.loadThingModel(productCode, version);
  }

  /** 读取数据库 IoT Product 的完整多语言名称。 */
  async function loadIotProductI18n(
    product: IotDatabaseProductCatalogItem,
  ): Promise<void> {
    await businessApplication.iotProducts.loadProductI18n(product);
  }

  /** 完整替换数据库 IoT Product 的多语言名称。 */
  async function replaceIotProductI18n(
    product: IotDatabaseProductCatalogItem,
    i18n: LocalizedFields,
  ): Promise<void> {
    await businessApplication.iotProducts.replaceProductI18n(product, i18n);
  }

  /** 发布数据库 IoT Product 的新物模型版本。 */
  async function publishIotThingModel(
    product: IotDatabaseProductCatalogItem,
    input: PublishIotThingModelInput,
  ): Promise<void> {
    await businessApplication.iotProducts.publishThingModel(product, input);
  }

  /** 读取数据库物模型版本的完整多语言字段。 */
  async function loadIotThingModelI18n(
    product: IotDatabaseProductCatalogItem,
    version: number,
  ): Promise<void> {
    await businessApplication.iotProducts.loadThingModelI18n(product, version);
  }

  /** 完整替换数据库物模型版本的多语言字段。 */
  async function replaceIotThingModelI18n(
    product: IotDatabaseProductCatalogItem,
    version: number,
    i18n: LocalizedFields,
  ): Promise<void> {
    await businessApplication.iotProducts.replaceThingModelI18n(
      product,
      version,
      i18n,
    );
  }

  /** 清除离开 IoT Product 详情后不再使用的状态。 */
  function clearIotProductDetails(): void {
    businessApplication.iotProducts.clearDetails();
  }

  /** 读取账号分页。 */
  async function loadAccounts(
    filters: Partial<AccountFilters> = {},
  ): Promise<void> {
    await businessApplication.accounts.load(filters);
  }

  /** 读取一个完整账号详情，供资源引用深链接打开现有抽屉。 */
  async function loadAccount(accountId: Int64String): Promise<PlatformAccount> {
    return businessApplication.accounts.require(accountId);
  }

  /** 创建完整账号。 */
  async function createAccount(
    input: CreateAccountInput,
  ): Promise<PlatformAccount> {
    return businessApplication.accounts.create(input);
  }

  /** 修改账号状态。 */
  async function changeAccountStatus(
    account: PlatformAccount,
    status: AccountStatus,
  ): Promise<void> {
    await businessApplication.accounts.changeStatus(account, status);
  }

  /** 读取账号角色。 */
  async function loadAccountRoles(accountId: Int64String): Promise<void> {
    await businessApplication.accounts.loadRoles(accountId);
  }

  /** 替换账号角色。 */
  async function replaceAccountRoles(
    roleIds: readonly Int64String[],
  ): Promise<void> {
    await businessApplication.accounts.replaceRoles(roleIds);
  }

  /** 读取角色列表。 */
  async function loadRoles(): Promise<void> {
    await businessApplication.roles.load();
  }

  /** 读取一个完整角色详情，供资源引用深链接打开现有窗口。 */
  async function loadRole(roleId: Int64String): Promise<PlatformRole> {
    return businessApplication.roles.require(roleId);
  }

  /** 创建角色。 */
  async function createRole(
    code: string,
    i18n: LocalizedFields,
  ): Promise<void> {
    await businessApplication.roles.create(code, i18n);
  }

  /** 修改角色状态。 */
  async function changeRoleStatus(
    role: PlatformRole,
    status: RoleStatus,
  ): Promise<void> {
    await businessApplication.roles.changeStatus(role, status);
  }

  /** 读取应用支持和默认使用的 Locale。 */
  async function loadRoleLocales(): Promise<void> {
    await businessApplication.roles.loadLocales();
  }

  /** 读取角色完整多语言字段。 */
  async function loadRoleI18n(roleId: Int64String): Promise<void> {
    await businessApplication.roles.loadI18n(roleId);
  }

  /** 原子替换角色完整多语言字段。 */
  async function replaceRoleI18n(i18n: LocalizedFields): Promise<void> {
    await businessApplication.roles.replaceI18n(i18n);
  }

  /** 删除非内置角色。 */
  async function deleteRole(role: PlatformRole): Promise<void> {
    await businessApplication.roles.delete(role);
  }

  /** 创建数据库动态字典。 */
  async function createDictionary(input: CreateDictionaryInput): Promise<void> {
    await businessApplication.systemDictionaries.create(input);
  }

  /** 给动态字典或可扩展代码字典创建数据库选项。 */
  async function createDictionaryOption(
    dictionaryKey: string,
    input: CreateDictionaryOptionInput,
  ): Promise<void> {
    await businessApplication.systemDictionaries.createOption(
      dictionaryKey,
      input,
    );
  }

  /** 删除一个数据库动态字典。 */
  async function deleteDictionary(
    dictionary: ManagedDictionary,
  ): Promise<void> {
    await businessApplication.systemDictionaries.delete(dictionary);
  }

  /** 删除一个数据库字典项。 */
  async function deleteDictionaryOption(
    dictionary: ManagedDictionary,
    option: ManagedDictionaryOption,
  ): Promise<void> {
    await businessApplication.systemDictionaries.deleteOption(
      dictionary,
      option,
    );
  }

  /** 读取所有字典管理结果。 */
  async function loadDictionaries(): Promise<void> {
    await businessApplication.systemDictionaries.load();
  }

  /** 读取业务表单需要的聚合字典选择项。 */
  async function loadDictionarySelections(
    keys: readonly string[],
  ): Promise<void> {
    await businessApplication.systemDictionaries.loadSelections(keys);
  }

  /** 按乐观锁版本修改一个数据库动态字典。 */
  async function updateDictionary(
    dictionary: ManagedDictionary,
    input: UpdateDictionaryInput,
  ): Promise<void> {
    await businessApplication.systemDictionaries.update(dictionary, input);
  }

  /** 按乐观锁版本修改一个数据库字典项。 */
  async function updateDictionaryOption(
    dictionary: ManagedDictionary,
    option: ManagedDictionaryOption,
    input: UpdateDictionaryOptionInput,
  ): Promise<void> {
    await businessApplication.systemDictionaries.updateOption(
      dictionary,
      option,
      input,
    );
  }

  /** 读取当前应用已注册的全部系统设置。 */
  async function loadSystemSettings(): Promise<void> {
    await businessApplication.systemSettings.load();
  }

  /** 恢复一个系统设置的 Java 默认值。 */
  async function resetSystemSetting(setting: SystemSetting): Promise<void> {
    await businessApplication.systemSettings.reset(setting);
  }

  /** 读取全部联邦登录 Provider。 */
  async function loadFederatedProviders(): Promise<void> {
    await businessApplication.federatedProviders.load();
  }

  /** 读取一个 Provider 的最新展示信息。 */
  async function loadFederatedProvider(
    code: string,
  ): Promise<FederatedProvider> {
    return businessApplication.federatedProviders.require(code);
  }

  /** 读取一个 Provider 的协议配置摘要。 */
  async function loadFederatedProviderSettings(
    code: string,
  ): Promise<FederatedProviderSettings> {
    return businessApplication.federatedProviders.loadSettings(code);
  }

  /** 创建默认停用的联邦 Provider。 */
  async function createFederatedProvider(
    input: CreateFederatedProviderInput,
  ): Promise<FederatedProvider> {
    return businessApplication.federatedProviders.create(input);
  }

  /** 替换 Provider 的登录页展示设置。 */
  async function replaceFederatedProvider(
    provider: FederatedProvider,
    input: ReplaceFederatedProviderInput,
  ): Promise<FederatedProvider> {
    return businessApplication.federatedProviders.replace(provider, input);
  }

  /** 替换 Provider 协议配置，空 Secret 保留已保存值。 */
  async function replaceFederatedProviderSettings(
    provider: FederatedProvider,
    input: FederatedProviderSettingsInput,
  ): Promise<void> {
    await businessApplication.federatedProviders.replaceSettings(
      provider,
      input,
    );
  }

  /** 修改 Provider 启用状态。 */
  async function setFederatedProviderStatus(
    provider: FederatedProvider,
    status: 'ACTIVE' | 'DISABLED',
  ): Promise<void> {
    await businessApplication.federatedProviders.setStatus(provider, status);
  }

  /** 删除停用且没有绑定身份的 Provider。 */
  async function deleteFederatedProvider(
    provider: FederatedProvider,
  ): Promise<void> {
    await businessApplication.federatedProviders.delete(provider);
  }

  /** 用 Codec 可解析文本覆盖一个系统设置。 */
  async function replaceSystemSetting(
    setting: SystemSetting,
    value: unknown,
  ): Promise<void> {
    await businessApplication.systemSettings.replace(setting, value);
  }

  /** 读取全部 SMTP 配置。 */
  async function loadEmailSenders(): Promise<void> {
    await businessApplication.emailSenders.load();
  }

  /** 创建默认停用的 SMTP 配置。 */
  async function createEmailSender(
    input: CreateEmailSenderInput,
  ): Promise<EmailSender> {
    return businessApplication.emailSenders.create(input);
  }

  /** 读取 SMTP 配置详情及其 ETag。 */
  async function loadEmailSender(senderId: Int64String): Promise<EmailSender> {
    return businessApplication.emailSenders.require(senderId);
  }

  /** 清除当前 SMTP 配置详情。 */
  function clearEmailSender(): void {
    businessApplication.emailSenders.clearDetail();
  }

  /** 按详情 ETag 完整替换 SMTP 配置。 */
  async function replaceEmailSender(
    input: ReplaceEmailSenderInput,
  ): Promise<EmailSender> {
    return businessApplication.emailSenders.replace(input);
  }

  /** 按最新详情 ETag 启用或停用 SMTP 配置。 */
  async function setEmailSenderEnabled(
    senderId: Int64String,
    enabled: boolean,
  ): Promise<void> {
    await businessApplication.emailSenders.setEnabled(senderId, enabled);
  }

  /** 测试一个 SMTP 配置能否建立连接。 */
  async function testEmailSenderConnection(
    senderId: Int64String,
  ): Promise<void> {
    await businessApplication.emailSenders.testConnection(senderId);
  }

  /** 按最新详情 ETag 删除停用 SMTP 配置。 */
  async function deleteEmailSender(senderId: Int64String): Promise<void> {
    await businessApplication.emailSenders.delete(senderId);
  }

  /** 同时读取指定 Email 模板页和应用 Locale。 */
  async function loadEmailTemplates(page?: number, size?: number): Promise<void> {
    await businessApplication.emailTemplates.load(page, size);
  }

  /** 创建默认停用的多语言 Email 模板。 */
  async function createEmailTemplate(
    input: EmailTemplateInput,
  ): Promise<EmailTemplate> {
    return businessApplication.emailTemplates.create(input);
  }

  /** 读取 Email 模板详情及其 ETag。 */
  async function loadEmailTemplate(
    templateId: Int64String,
  ): Promise<EmailTemplate> {
    return businessApplication.emailTemplates.require(templateId);
  }

  /** 清除当前 Email 模板详情和预览。 */
  function clearEmailTemplate(): void {
    businessApplication.emailTemplates.clearDetail();
  }

  /** 按详情 ETag 完整替换 Email 模板。 */
  async function replaceEmailTemplate(
    input: EmailTemplateInput,
  ): Promise<EmailTemplate> {
    return businessApplication.emailTemplates.replace(input);
  }

  /** 按最新详情 ETag 启用或停用 Email 模板。 */
  async function setEmailTemplateEnabled(
    templateId: Int64String,
    enabled: boolean,
  ): Promise<void> {
    await businessApplication.emailTemplates.setEnabled(templateId, enabled);
  }

  /** 使用未保存草稿生成服务端安全预览。 */
  async function previewEmailTemplate(
    input: PreviewEmailTemplateInput,
  ): Promise<RenderedEmail> {
    return businessApplication.emailTemplates.preview(input);
  }

  /** 按最新详情 ETag 删除停用 Email 模板。 */
  async function deleteEmailTemplate(templateId: Int64String): Promise<void> {
    await businessApplication.emailTemplates.delete(templateId);
  }

  /** 读取全部 SMS Provider。 */
  async function loadSmsProviders(): Promise<void> {
    await businessApplication.smsNotifications.loadProviders();
  }

  /** 创建默认停用 SMS Provider。 */
  async function createSmsProvider(
    input: SmsProviderInput,
  ): Promise<SmsProvider> {
    return businessApplication.smsNotifications.createProvider(input);
  }

  /** 读取 SMS Provider 详情和 ETag。 */
  async function loadSmsProvider(
    providerId: Int64String,
  ): Promise<SmsProvider> {
    return businessApplication.smsNotifications.requireProvider(providerId);
  }

  /** 完整替换停用 SMS Provider。 */
  async function replaceSmsProvider(
    input: SmsProviderInput,
  ): Promise<SmsProvider> {
    return businessApplication.smsNotifications.replaceProvider(input);
  }

  /** 修改 SMS Provider 状态。 */
  async function setSmsProviderEnabled(
    providerId: Int64String,
    enabled: boolean,
  ): Promise<void> {
    await businessApplication.smsNotifications.setProviderEnabled(
      providerId,
      enabled,
    );
  }

  /** 删除停用且没有绑定的 SMS Provider。 */
  async function deleteSmsProvider(providerId: Int64String): Promise<void> {
    await businessApplication.smsNotifications.deleteProvider(providerId);
  }

  /** 读取指定 Provider 的模板绑定。 */
  async function loadSmsBindings(providerId: Int64String): Promise<void> {
    await businessApplication.smsNotifications.loadBindings(providerId);
  }

  /** 创建默认停用 SMS Provider 模板绑定。 */
  async function createSmsBinding(
    providerId: Int64String,
    input: SmsProviderBindingInput,
  ): Promise<SmsProviderBinding> {
    return businessApplication.smsNotifications.createBinding(
      providerId,
      input,
    );
  }

  /** 读取 SMS Provider 模板绑定详情和 ETag。 */
  async function loadSmsBinding(
    providerId: Int64String,
    bindingId: Int64String,
  ): Promise<SmsProviderBinding> {
    return businessApplication.smsNotifications.requireBinding(
      providerId,
      bindingId,
    );
  }

  /** 完整替换停用 SMS Provider 模板绑定。 */
  async function replaceSmsBinding(
    input: SmsProviderBindingInput,
  ): Promise<SmsProviderBinding> {
    return businessApplication.smsNotifications.replaceBinding(input);
  }

  /** 修改 SMS Provider 模板绑定状态。 */
  async function setSmsBindingEnabled(
    providerId: Int64String,
    bindingId: Int64String,
    enabled: boolean,
  ): Promise<void> {
    await businessApplication.smsNotifications.setBindingEnabled(
      providerId,
      bindingId,
      enabled,
    );
  }

  /** 删除停用 SMS Provider 模板绑定。 */
  async function deleteSmsBinding(
    providerId: Int64String,
    bindingId: Int64String,
  ): Promise<void> {
    await businessApplication.smsNotifications.deleteBinding(
      providerId,
      bindingId,
    );
  }

  /** 清除 SMS Provider 详情。 */
  function clearSmsProvider(): void {
    businessApplication.smsNotifications.clearProvider();
  }

  /** 清除 SMS Provider 模板绑定详情。 */
  function clearSmsBinding(): void {
    businessApplication.smsNotifications.clearBinding();
  }

  /** 读取角色权限树。 */
  async function loadRolePermissions(roleId: Int64String): Promise<void> {
    await businessApplication.roles.loadPermissions(roleId);
  }

  /** 替换角色权限。 */
  async function replaceRolePermissions(
    codes: readonly string[],
  ): Promise<void> {
    await businessApplication.roles.replacePermissions(codes);
  }

  /** 读取只读权限目录。 */
  async function loadPermissions(): Promise<void> {
    await businessApplication.permissions.load();
  }

  /** 读取操作日志分页。 */
  async function loadOperationLogs(
    filters: Partial<OperationLogFilters> = {},
  ): Promise<void> {
    await businessApplication.operationLogs.load(filters);
  }

  /** 读取一条操作日志详情。 */
  async function loadOperationLog(id: Int64String): Promise<void> {
    await businessApplication.operationLogs.require(id);
  }

  /** 清除当前操作日志详情。 */
  function clearOperationLog(): void {
    businessApplication.operationLogs.clearSelection();
  }

  /** 读取当前资料。 */
  async function loadProfile(): Promise<void> {
    await businessApplication.profile.load();
    syncCurrentUserName();
  }

  /** 完整替换当前资料。 */
  async function saveProfile(
    displayName: string,
    email: null | string,
    phoneNumber: null | string,
  ): Promise<void> {
    await businessApplication.profile.save(displayName, email, phoneNumber);
    syncCurrentUserName();
  }

  /** 上传头像文件，不区分后端签发的是 App 路由还是对象存储地址。 */
  async function uploadAvatar(
    accountId: Int64String,
    file: File,
  ): Promise<Int64String> {
    return businessApplication.files.uploadAvatar(accountId, {
      filename: file.name,
      contentType: file.type,
      sizeBytes: file.size,
      body: await file.arrayBuffer(),
    });
  }

  /** 批量读取头像短期地址；图片加载失败时可以要求强制换签。 */
  async function resolveAvatarUrls(
    fileIds: readonly Int64String[],
    forceRefresh = false,
  ): Promise<ReadonlyMap<Int64String, string>> {
    return businessApplication.files.resolveAccessUrls(fileIds, forceRefresh);
  }

  /** 替换当前账号头像并更新资料状态。 */
  async function replaceProfileAvatar(fileId: Int64String): Promise<void> {
    await businessApplication.profile.replaceAvatar(fileId);
  }

  /** 删除当前账号头像并更新资料状态。 */
  async function deleteProfileAvatar(): Promise<void> {
    await businessApplication.profile.deleteAvatar();
  }

  /** 替换指定 Platform Account 的头像并更新当前账号页。 */
  async function replaceAccountAvatar(
    accountId: Int64String,
    fileId: Int64String,
  ): Promise<void> {
    await businessApplication.accounts.replaceAvatar(accountId, fileId);
  }

  /** 删除指定 Platform Account 的头像并更新当前账号页。 */
  async function deleteAccountAvatar(accountId: Int64String): Promise<void> {
    await businessApplication.accounts.deleteAvatar(accountId);
  }

  /** 开始账号删除 verification。 */
  async function beginAccountDeletion(
    account: PlatformAccount,
  ): Promise<AccountDeletionOutcome> {
    return businessApplication.accounts.beginDeletion(account);
  }

  /** 提交删除账号 verification 要求的密码。 */
  async function submitAccountDeletionPassword(
    currentPassword: string,
  ): Promise<AccountDeletionOutcome> {
    return businessApplication.accounts.submitDeletionPassword(currentPassword);
  }

  /** 提交删除账号 verification 要求的 TOTP。 */
  async function submitAccountDeletionTotp(
    code: string,
  ): Promise<AccountDeletionOutcome> {
    return businessApplication.accounts.submitDeletionTotp(code);
  }

  /** 提交删除账号 verification 要求的 Passkey。 */
  async function submitAccountDeletionPasskey(): Promise<AccountDeletionOutcome> {
    const proof = await browserPasskeyClient.getAssertion(
      await businessApplication.accounts.createDeletionPasskeyOptions(),
    );
    return businessApplication.accounts.submitDeletionPasskey(proof);
  }

  /** 取消尚未完成的账号删除。 */
  function cancelAccountDeletion(): void {
    businessApplication.accounts.cancelDeletion();
  }

  /** 把个人资料中的显示名称同步到 Vben 用户菜单。 */
  function syncCurrentUserName(): void {
    const current = userStore.userInfo;
    const displayName = profile.value.profile?.displayName;
    if (current && displayName) {
      userStore.setUserInfo({ ...current, realName: displayName });
    }
  }

  return {
    accounts,
    beginAccountDeletion,
    cancelAccountDeletion,
    changeAccountStatus,
    changeIotProductStatus,
    changeRoleStatus,
    clearEmailSender,
    clearEmailTemplate,
    clearSmsBinding,
    clearSmsProvider,
    clearOperationLog,
    clearIotProductDetails,
    createAccount,
    createDictionary,
    createDictionaryOption,
    createEmailSender,
    createEmailTemplate,
    createFederatedProvider,
    createIotProduct,
    createRole,
    createSmsBinding,
    createSmsProvider,
    deleteAccountAvatar,
    deleteDictionary,
    deleteDictionaryOption,
    deleteEmailSender,
    deleteEmailTemplate,
    deleteFederatedProvider,
    deleteProfileAvatar,
    deleteRole,
    deleteSmsBinding,
    deleteSmsProvider,
    emailSenders,
    emailTemplates,
    loadAccountRoles,
    loadAccount,
    loadAccounts,
    loadDictionaries,
    loadDictionarySelections,
    loadEmailSender,
    loadEmailSenders,
    loadEmailTemplate,
    loadEmailTemplates,
    loadFederatedProvider,
    loadFederatedProviders,
    loadFederatedProviderSettings,
    loadIotProductI18n,
    loadIotProducts,
    loadIotThingModel,
    loadIotThingModelI18n,
    loadIotThingModels,
    loadOperationLog,
    loadOperationLogs,
    loadPermissions,
    loadProfile,
    loadRoleI18n,
    loadRole,
    loadRoleLocales,
    loadRolePermissions,
    loadRoles,
    loadSystemSettings,
    loadSmsBinding,
    loadSmsBindings,
    loadSmsProvider,
    loadSmsProviders,
    operationLogs,
    iotProducts,
    permissions,
    previewEmailTemplate,
    profile,
    replaceAccountAvatar,
    replaceAccountRoles,
    replaceEmailSender,
    replaceEmailTemplate,
    replaceFederatedProvider,
    replaceFederatedProviderSettings,
    replaceIotProductI18n,
    replaceIotThingModelI18n,
    replaceProfileAvatar,
    replaceRoleI18n,
    replaceRolePermissions,
    replaceSystemSetting,
    replaceSmsBinding,
    replaceSmsProvider,
    resetSystemSetting,
    resolveAvatarUrls,
    roles,
    publishIotThingModel,
    saveProfile,
    session,
    setSmsBindingEnabled,
    setSmsProviderEnabled,
    setEmailSenderEnabled,
    setEmailTemplateEnabled,
    setFederatedProviderStatus,
    submitAccountDeletionPasskey,
    submitAccountDeletionPassword,
    submitAccountDeletionTotp,
    systemDictionaries,
    systemSettings,
    smsNotifications,
    federatedProviders,
    testEmailSenderConnection,
    updateDictionary,
    updateDictionaryOption,
    updateIotProduct,
    uploadAvatar,
  };
});
