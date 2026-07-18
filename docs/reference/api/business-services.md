# 业务服务

## 认证与 IAM

| 包 | 接口 | 主要方法 | 默认实现 |
| --- | --- | --- | --- |
| `com.sca4d.authentication` | `AuthenticationProcessService` | 创建认证、提交具体认证方式、创建 Session 或完成 STEP_UP | `FlowableAuthenticationProcessService` |
| `com.sca4d.authentication` | `SessionService` | `create(...)`、`refreshAfterCommit(...)` | `DefaultSessionService` |
| `com.sca4d.authentication.me` | `MeService` | `snapshot(...)` | `DefaultMeService` |
| `com.sca4d.iam.credential` | `CredentialService` | `bind(...)`、`replace(...)`、`verify(...)`、`changeStatus(...)`、`delete(...)` | `DefaultCredentialService` |
| `com.sca4d.iam.subject` | `SubjectService` | `create(...)`、`require(...)`、`update(...)`、`changeStatus(...)`、`delete(...)` | `DefaultSubjectService` |
| `com.sca4d.iam.account` | `AccountService` | `create(...)`、`require(...)`、`findBySubject(...)` | `DefaultAccountService` |
| `com.sca4d.iam.identifier` | `AccountIdentifierService` | `createUsername(...)`、`findActive(...)`、`findByAccount(...)` | `DefaultAccountIdentifierService` |
| `com.sca4d.iam.contact` | `ContactService` | `replace(...)` | `DefaultContactService` |
| `com.sca4d.iam.principal` | `PrincipalService` | 创建、查询和修改 DEFAULT、PLATFORM、TENANT Principal，以及平台角色绑定 | `DefaultPrincipalService` |
| `com.sca4d.tenant` | `TenantAccessService` | 查询和原子替换 PLATFORM Principal 的租户 ACL | `DefaultTenantAccessService` |

`require(...)` 在记录不存在时抛出所属模块的 NOT_FOUND 错误；带 version 的写方法在版本变化时抛出并发错误。

## 账号、租户和角色

| 包 | 接口 | 主要方法 | 默认实现 |
| --- | --- | --- | --- |
| `com.sca4d.iam.account` | `AccountProvisioningService` | `create(...)`；同时创建 Subject、DEFAULT Principal 和 DEFAULT Profile | `DefaultAccountProvisioningService` |
| `com.sca4d.tenant` | `TenantService` | `page(...)`、`require(...)`、`requireActive(...)`、`create(...)`、`update(...)`、`changeStatus(...)`、`memberships(...)` | `DefaultTenantService` |
| `com.sca4d.tenant` | `TenantMemberService` | `page(...)`、`require(...)`、`createNewAccount(...)`、`addExistingAccount(...)`、角色和状态修改 | `DefaultTenantMemberService` |
| `com.sca4d.role` | `RoleService` | `page(...)`、`detail(...)`、`assignablePermissions(...)`、`create(...)`、`update(...)`、`replacePermissions(...)`、`delete(...)` | `DefaultRoleService` |

平台调用使用 `tenantId = null`；租户角色和成员方法必须传入 tenantId。状态、租户归属和角色类型不符合时返回所属模块的业务错误。

## 系统功能

| 包 | 接口 | 主要方法 | 默认实现 |
| --- | --- | --- | --- |
| `com.sca4d.system` | `MenuService` | `menus()`、`routes()`、`create(...)`、`update(...)`、`delete(...)`、`navigation(...)` | `DefaultMenuService` |
| `com.sca4d.system` | `DictionaryService` | 字典类型、字典项的分页、创建、修改、删除和排序 | `DefaultDictionaryService` |
| `com.sca4d.system` | `ErrorCodeService` | `catalog()` | `DefaultErrorCodeService` |
| `com.sca4d.system` | `OperationLogService` | `catalog()`、`pageOperatorOptions(...)`、`page(...)`、`detail(...)` | `DefaultOperationLogService` |
| `com.sca4d.notification` | `NotificationService` | `create(...)`、`page(...)`、`unreadCount(...)`、`read(...)`、`readAll(...)` | `NotificationServiceImpl` |
| `com.sca4d.im` | `ImService` | `conversations(...)`、`createConversation(...)`、`messages(...)`、`send(...)`、`read(...)`、`revoke(...)` | `ImServiceImpl` |

## 代码字典

| 包 | 类型 | 主要成员或结果 |
| --- | --- | --- |
| `com.sca4d.common.dictionary` | `@CodeDictionary` | code、nameKey、mode |
| `com.sca4d.common.dictionary` | `@CodeDictionaryItem` | labelKey、tone、selectable、published |
| `com.sca4d.common.dictionary` | `CodeDictionaryMode` | `CLOSED`、`EXTENSIBLE` |
| `com.sca4d.common.dictionary` | `DictionarySource` | `CODE`、`CONFIGURATION` |
| `com.sca4d.common.dictionary` | `CodeDictionaryScanner` | `scan(basePackages)`；只接受实现 `BaseEnum` 的枚举 |
| `com.sca4d.common.dictionary` | `CodeDictionaryCatalog` | `definitions()`、`find(code)`；拒绝重复编码和值 |
| `com.sca4d.common.dictionary` | `CodeDictionaryDefinition` | code、mode、source、nameKey、sourceRef、options |
| `com.sca4d.system.repository` | `DictionaryRepository` | 同步代码字典，查询和修改可配置字典 |
| `com.sca4d.system` | `DictionaryValueReference` | `supports(...)`、`findReferencedValues(...)` |
| `com.sca4d.system` | `DictionaryReferenceRegistry` | `referencedValues(...)`；拒绝实现返回候选集之外的值 |
| `com.sca4d.system` | `DictionaryBootstrap` | 应用就绪时把代码字典同步到数据库 |

## 领域持久化类型

| 包 | 类型 | 主要方法或内容 |
| --- | --- | --- |
| `com.sca4d.iam.account` | `AccountService` | Account 查询和认证安全状态管理 |
| `com.sca4d.system.repository` | `OperationLogRepository` | 操作者分页、审计追加、日志分页和详情 |
| `com.sca4d.notification.repository` | `NotificationRepository` | 通知与接收记录写入、分页、未读统计和已读更新 |
| `com.sca4d.im.repository` | `ImRepository` | 会话、成员、消息、已读和撤回持久化 |
| `com.sca4d.role` | `ManagedRole` | 角色详情和权限集合 |
| `com.sca4d.role` | `ManagedPermission` | 可分配权限 |
| `com.sca4d.role.repository` | `RoleUsageCount` | 角色使用数量投影 |
| `com.sca4d.notification` | `NotificationMessage` | 通知与当前接收记录的读取类型 |
| `com.sca4d.notification.codec` | `NotificationPayloadCodec` | `encode(...)`；把可空扩展字段编码为 JSON |

## 管理入口与租户授权

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `com.sca4d.controllers.mg.platform` | `PlatformAccountController` | GLOBAL Account 和认证安全子资源 HTTP 接口 |
| `com.sca4d.controllers.mg.platform` | `PlatformPrincipalController` | 平台操作员、平台角色和租户 ACL HTTP 接口 |
| `com.sca4d.controllers.mg.platform` | `PlatformPrincipalTenantAccessController` | PLATFORM Principal 的租户 ACL 查询和替换 |
| `com.sca4d.tenant.request` | `TenantAccessReplaceRequest` | defaultAction、grants、version、idempotencyKey |
| `com.sca4d.tenant.view` | `PlatformTenantAccessView` | 当前 ACL、Principal 版本和租户资源引用 |
| `com.sca4d.iam.resource` | `IamResourceReferenceContributor` | 批量解析 Subject、租户、成员、凭据和角色引用 |
| `com.sca4d.tenant` | `TenantAuditOperations` | 注册租户管理审计操作 |
| `com.sca4d.controllers.biz.im` | `ImStompController` | STOMP IM 发送和已读入口 |
