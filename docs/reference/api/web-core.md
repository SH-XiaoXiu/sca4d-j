# Web 与通用类型

## HTTP 响应与分页

包：`com.sca4d.common.web`

| 类型 | 主要成员 | 限制 |
| --- | --- | --- |
| `ApiResponse<T>` | `ok(data)`、`ok()`、`fail(...)`、`toResponseEntity(...)` | 成功使用 `ok`；业务失败由异常处理器创建 |
| `PageQuery` | `page()`、`size()`、`toPage()` | page 默认 1，size 默认 20，size 最大 100 |
| `SortQuery` | `field()`、`direction()` | 业务代码必须限制允许的排序字段 |
| `PageData<T>` | `empty(...)`、`from(...)`、`map(...)` | 包含 records、page、size、total |
| `CursorQuery` | `cursor()`、`size()` | cursor 可空，size 最大 100 |
| `CursorPageData<T>` | `of(...)` | 包含 records、nextCursor、hasMore |
| `GlobalExceptionHandler` | Spring `@ExceptionHandler` 方法 | 将业务、校验、并发、限流和系统异常转换为 `ApiResponse` |

成功响应：

```java
return ApiResponse.ok(ManagedAccountView.from(account));
```

分页映射：

```java
return ApiResponse.ok(service.page(request.page(), request.size())
        .map(ManagedAccountView::from));
```

响应字段和 Header 见 [HTTP 接口](/reference/http.md#响应格式)。

## 响应 JSON 修饰

包：`com.sca4d.common.web.view`

| 类型 | 主要成员 | 用途 |
| --- | --- | --- |
| `BaseView` | `apply(ViewJson)` | 在 Jackson 输出阶段修改当前 View 的 JSON |
| `ViewJson` | `put`、`opt`、`remove`、`rename`、`merge`、`edit`、`list` | 修改已经序列化的 ObjectNode |
| `DataOps` | `text`、`value`、`seq`、`json` | 空值、文本和集合转换 |
| `BaseViewSerializer` | `serialize(...)` | 先委托默认 serializer，再调用 `BaseView.apply` |
| `BaseViewSerializerModifier` | `modifySerializer(...)` | 为 `BaseView` 安装 serializer |

`BaseView.apply` 不得查询数据库或调用 Service。需要关联数据时，在 Controller 返回 View 前完成查询。

## ClientState

包：`com.sca4d.common.web`

| 类型 | 主要成员 | 输出 |
| --- | --- | --- |
| `ClientStateTarget` | `of(value)`、`resource(resourceType, resourceId)` | 校验固定目标或生成 `resource:id` 动态目标 |
| `ClientState` | `stale()`、`refresh(ClientStateTarget)`、`version(value)` | 分别保存整体过期、精确刷新目标和版本；`refresh` 不隐式设置 stale |
| `ClientStateFilter` | Servlet Filter | 请求结束时清理 ClientState |
| `ClientStateResponseAdvice` | ResponseBodyAdvice | 写入 `X-Client-State-Stale`、`X-Client-State-Refresh`、`X-Client-State-Version` |

固定刷新目标由所属模块的 `AuthenticationClientStateTargets`、`IamClientStateTargets`、`IdentityClientStateTargets`、`MailClientStateTargets`、`SystemClientStateTargets` 和 `TenantClientStateTargets` 声明。common Web 组件不维护业务目标清单。

## 枚举与国际化

| 包 | 类型 | 主要成员 |
| --- | --- | --- |
| `com.sca4d.common.enums` | `BaseEnum` | `value()`、`description()`、`fromValue(...)` |
| `com.sca4d.common.enums` | `EnumValueCodec` | `encode(...)`、`decode(...)` |
| `com.sca4d.common.enums` | `BaseEnumTypeHandler<E>` | MyBatis 字符串枚举转换 |
| `com.sca4d.common.i18n` | `MessageReference` | `of(key, arguments)` |
| `com.sca4d.common.i18n` | `LocalizedMessages` | `get(key, arguments)`、`get(locale, key, arguments)` |
| `com.sca4d.common.i18n` | `SupportedLocale` | `ZH_CN`、`EN_US`、`locales()`、`supports(...)` |
| `com.sca4d.common.i18n` | `@SupportedLanguage` | 校验 `zh-CN` 和 `en-US` 语言键 |
| `com.sca4d.common.i18n` | `LocaleResponseInterceptor` | 解析语言并写入 Content-Language |

## ID 与资源引用

| 包 | 类型 | 主要成员 |
| --- | --- | --- |
| `com.sca4d.common.id` | `IdGenerator` | `nextId()` |
| `com.sca4d.common.id` | `ClusterIdGenerator` | workerId 和 dataCenterId 生成集群 ID |
| `com.sca4d.common.id` | `ClusterIds` | `nextId()`、`nextIdStr()` |
| `com.sca4d.common.id` | `MybatisFlexClusterIdKeyGenerator` | MyBatis-Flex 主键生成器 |
| `com.sca4d.common.resource` | `ResourceReferenceKey` | resourceType + resourceId |
| `com.sca4d.common.resource` | `ResourceReference` | label、tenantId、navigationState、path、navigationMessage |
| `com.sca4d.common.resource` | `ResourceReferenceContext` | 当前 Principal 和权限集合 |
| `com.sca4d.common.resource` | `ResourceReferenceContributor` | `resourceType()`、批量解析资源引用 |
| `com.sca4d.common.resource` | `ResourceReferenceResolver` | 批量合并 Contributor 结果 |
| `com.sca4d.common.resource` | `DefaultResourceReferenceResolver` | 默认 Resolver |
| `com.sca4d.common.resource.view` | `ResourceReferenceView` | HTTP 输出类型 |

`ResourceReferenceView.label` 只返回当前请求允许展示的业务名称，名称未知或无权查看时为空。`navigationMessage` 只在资源不可导航时返回本地化原因。`DELETED` 且没有历史名称快照时不会使用删除原因、资源 ID 或资源类型伪造名称。

## 输入校验

校验注解的参数和默认值见[接口与注解](/reference/interfaces-annotations.md#输入校验)。`ValidationPatterns` 提供业务编码、命名空间编码和 MAC 地址正则；Validator 允许 `null`，必填字段需同时使用 `@NotNull` 或 `@NotBlank`。

`com.sca4d.common.net.HttpEndpoints` 提供 `parseSecure(String)` 和 `isSecure(URI)`。外部 HTTP 端点必须使用 HTTPS；`localhost`、`127.0.0.1` 和 `::1` 允许使用本机测试 HTTP。协议模块仍负责 issuer query、Passkey Origin 和 LDAP URI 等额外规则。

## 错误码扫描

包：`com.sca4d.common.exception`

| 类型 | 主要方法 | 限制 |
| --- | --- | --- |
| `ErrorCodeScanner` | `scan(basePackages)` | 只返回实现 `BizErrorCode` 的枚举；号段由 Catalog 校验 |
| `ErrorCodeCatalog` | `common()`、`builder()`、`items()`、`ranges()` | 拒绝号段重叠、代码超出号段和重复代码 |
| `ErrorCodeCatalog.Builder` | `register(enumType)`、`build()` | 枚举必须实现 `BizErrorCode` |

## 基础常量

| 包 | 类型 | 当前值 |
| --- | --- | --- |
| `com.sca4d.common.core` | `Constants` | `APP_CODE=sca4d-j`、`DEFAULT_ZONE_ID=Asia/Shanghai`、`UTF_8=UTF-8` |
| `com.sca4d.common.core` | `Profiles` | `local`、`dev`、`test`、`staging`、`prod` |
