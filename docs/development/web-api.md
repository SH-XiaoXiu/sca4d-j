# 添加 HTTP 接口

以下步骤使用通知列表说明分页 GET 接口的写法，并使用系统菜单说明写接口的写法。

## 前置条件

- Service 方法和返回类型已经存在。
- 接口需要的权限编码已经加入 `PermissionCatalog`。
- Request 只包含客户端输入，View 只包含客户端需要的输出。

## 1. 创建分页 Request

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationPageRequest extends PageQuery {

    private Boolean unreadOnly;
}
```

`PageQuery` 提供 `page()` 和 `size()`。页码从 1 开始，每页数量由 `PageQuery` 的校验限制。

## 2. 创建分页接口

```java
@RestController
@RequestMapping("/notifications")
@AuthPrincipal
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @RequirePermission(NotificationPermissions.MESSAGES_READ)
    public ApiResponse<PageData<NotificationView>> page(
            NotificationPageRequest request,
            @AuthSubject Subject subject,
            @AuthPrincipal Principal principal) {
        PageData<NotificationView> result = service.page(
                        Boolean.TRUE.equals(request.getUnreadOnly()),
                        request.page(),
                        request.size(),
                        subject,
                        principal)
                .map(NotificationView::from);
        return ApiResponse.ok(result);
    }
}
```

请求示例：

```http
GET /notifications?page=1&size=20&unreadOnly=true
Authorization: <tokenValue>
```

响应示例：

```json
{
  "code": 0,
  "messageKey": null,
  "messageArgs": [],
  "message": null,
  "data": {
    "records": [],
    "page": 1,
    "size": 20,
    "total": 0
  },
  "traceId": "6b6f2d2c7d5f4a34",
  "timestamp": 1780000000000
}
```

## 3. 创建写入 Request

```java
@Data
public class MenuCreateRequest implements IdempotentRequest {

    private Long parentId;

    @BizId(mode = BizIdMode.NAMESPACED_CODE, max = 128)
    private String routeCode;

    @NotEmpty
    private Map<@SupportedLanguage String,
            @Size(min = 1, max = 128) String> titles;

    @NotNull
    private MenuIconName icon;

    @NotNull
    private Boolean visible;

    private String idempotencyKey;
}
```

Bean Validation 负责空值、长度和格式。父菜单是否存在、routeCode 是否已注册、菜单是否形成循环等检查由 Service 完成。

## 4. 创建 POST 接口

```java
@PostMapping
@Idempotent(operation = "system.menus.create")
@Audit(
        module = "system.menus",
        action = "create",
        targetType = SystemResourceTypes.MENU,
        targetId = "#result.data")
@RequirePermission(SystemPermissions.MENUS_MANAGE)
public ApiResponse<Long> create(
        @Valid @RequestBody MenuCreateRequest request) {
    return ApiResponse.ok(menuService.create(
            request.getParentId(),
            request.getRouteCode(),
            request.getTitles(),
            request.getIcon(),
            request.getVisible()));
}
```

Controller 只处理 HTTP 参数、注解和 View 转换。数据库事务和菜单校验放在 Service。

## 5. 返回 View

View 使用 `from(...)` 转换业务对象：

```java
@Getter
@AllArgsConstructor
public class UnreadCountView {

    private final long unreadCount;
}
```

```java
@GetMapping("/unread-count")
@RequirePermission(NotificationPermissions.MESSAGES_READ)
public ApiResponse<UnreadCountView> unreadCount(
        @AuthSubject Subject subject,
        @AuthPrincipal Principal principal) {
    return ApiResponse.ok(new UnreadCountView(
            service.unreadCount(subject, principal)));
}
```

Service 不返回 View。Controller 不把 Entity 直接放入响应。

## 6. 处理匿名接口

接口默认需要登录。创建认证等公开接口显式添加 `@AnonymousAccess`：

```java
@AnonymousAccess
@PostMapping("/authentications")
public ApiResponse<AuthenticationView> createAuthentication(
        @Valid @RequestBody AuthenticationCreateRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse response) {
    String browserHash = authenticationBrowser.bind(servletRequest, response);
    return ApiResponse.created(AuthenticationView.from(authentications.createAnonymous(
            request.getPurpose(), request.getScopeType(), request.getTenantCode(),
            browserHash, request.getDeviceId(), request.getTerminalName())));
}
```

不要通过 Controller 自己读取 Header 或维护另一份匿名路径清单。

## 7. 返回错误

Service 抛出 `BizException` 和模块错误码。异常处理器生成 HTTP 状态与 `ApiResponse`：

```java
throw new BizException(SystemErrorCode.MENU_PARENT_NOT_FOUND);
```

消息键、HTTP 分类和稳定数值由错误码枚举声明。业务代码不向 `BizException` 传入自定义消息键；需要模板参数时直接跟在错误码后传入。

| 情况 | HTTP |
| --- | ---: |
| 参数或 JSON 错误 | 400 |
| 未登录 | 401 |
| 功能权限或数据访问失败 | 403 |
| 资源不存在 | 404 |
| version、幂等或状态冲突 | 409 |
| 请求过大 | 413 |
| Content-Type 不支持 | 415 |
| 限流 | 429 |

Controller 不捕获异常后返回 `ApiResponse.ok()`。

## 8. 处理 409 和客户端刷新

乐观锁冲突时异常处理器返回 409，并可设置：

```text
X-Client-State-Stale: true
X-Client-State-Refresh: resource
X-Client-State-Version: <version>
```

管理端 `ApiClient` 读取 Header，重新请求资源。不要自动使用旧 Request 再次覆盖。

## 9. 检查 OpenAPI

启动后端后访问：

```text
GET /v3/api-docs
GET /swagger-ui/index.html
```

然后运行：

```bash
cd sca4d-admin
pnpm api:generate
pnpm api:check
```

确认 Request 必填字段、枚举、Path 参数和响应类型与 Controller 一致。`@AuthSubject`、`@AuthAccount` 和 `@AuthPrincipal` 参数不应出现在 OpenAPI 中。

## 10. 添加测试

Controller 测试至少覆盖成功、400、401、403、404 和 409。写接口还要检查幂等 Header、ClientState Header 和操作日志 targetId。

## 运行结果

OpenAPI 中的 Path、Request 和 View 与实际响应一致，管理端生成类型通过 `api:check`，Controller 测试覆盖成功和主要失败状态。

## 失败处理

出现 400 时检查 Bean Validation 和 JSON 枚举值；出现 401 或 403 时分别检查登录状态、当前 Principal 和权限。`api:check` 失败时重新生成类型，并检查是否遗漏 Request 或 View 变更。

## 下一步

需要限制资源归属时，继续[添加权限和数据访问检查](./permission.md)。
