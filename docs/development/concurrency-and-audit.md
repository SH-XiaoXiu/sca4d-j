# 保护写接口并记录操作日志

以下步骤为创建和修改接口添加乐观锁、幂等、分布式锁、限流和操作日志。按接口实际风险选择，不需要在每个接口使用全部注解。

## 前置条件

- 写接口已经使用 Request、Service 和 Repository 实现。
- Redis 已启动；分布式锁、限流和幂等功能已启用。
- 数据表包含 `version` 字段，更新语句可以按版本判断影响行数。
- 需要记录的操作已经加入审计目录。

## 1. 防止旧数据覆盖新数据

更新 Request 实现 `VersionedRequest`：

```java
@Data
public class MenuUpdateRequest implements VersionedRequest {

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

    @NotNull
    private Long version;
}
```

Controller 把 version 传给 Service：

```java
@PutMapping("/{menuId}")
public ApiResponse<Void> update(
        @PathVariable Long menuId,
    @Valid @RequestBody MenuUpdateRequest request) {
    menuService.update(
            menuId,
            request.getParentId(),
            request.getRouteCode(),
            request.getTitles(),
            request.getIcon(),
            request.getVisible(),
            request.getVersion());
    return ApiResponse.ok();
}
```

Mapper 使用同一条 UPDATE 检查并增加版本：

```sql
UPDATE sys_menu
SET parent_id = #{parentId},
    permission_code = #{permissionCode},
    route_code = #{routeCode},
    title_labels = CAST(#{titleLabels} AS JSONB),
    icon_code = #{iconCode},
    visible = #{visible},
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = #{menuId}
  AND version = #{version}
  AND deleted = 0
```

影响行数为 `0` 时抛出 `ConcurrencyConflictException`。异常处理器返回 HTTP 409，并设置 `X-Client-State-Refresh`。不要先 SELECT version，再执行不带 version 的 UPDATE。

## 2. 防止重复创建

创建 Request 实现 `IdempotentRequest`：

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

在 Controller 上声明 operation：

```java
@PostMapping
@Idempotent(operation = "system.menus.create")
@RequirePermission(SystemPermissions.MENUS_MANAGE)
public ApiResponse<Long> create(
        @Valid @RequestBody MenuCreateRequest request) {
    Long menuId = menuService.create(
            request.getParentId(),
            request.getRouteCode(),
            request.getTitles(),
            request.getIcon(),
            request.getVisible());
    return ApiResponse.ok(menuId);
}
```

客户端第一次提交时生成 key。网络重试和重复点击使用同一个 key；用户开始一次新的创建操作时生成新 key。

处理结果：

| 情况 | 结果 |
| --- | --- |
| 同 key、同请求内容、第一次已成功 | 返回保存的成功响应，并设置 `Idempotency-Replayed` |
| 同 key、同请求内容、第一次仍在执行 | 返回 409 和 `Retry-After` |
| 同 key、不同请求内容 | 返回 409 |
| 缺少 key | 返回 400 |

数据库仍要保留业务唯一索引。例如菜单、租户编码或订单号重复时，唯一索引负责最后检查。

## 3. 限制多个实例同时修改同一对象

修改同一 Subject 的状态或其 Account、Credential 时使用相同 Subject 锁 key：

```java
@DistributedLock(key = "'subject:' + #subjectId")
@Transactional
public void changeStatus(
        @NotNull Long subjectId,
        @NotNull SubjectStatus status,
        long version) {
    // 在锁内重新查询并更新 Subject。
}
```

`waitTimeSeconds` 小于 `0` 时使用全局配置，`leaseTimeSeconds=0` 使用 Redisson watchdog。获取锁失败时返回冲突错误。锁内仍要使用数据库版本和唯一约束。

如果锁 key 来自多个参数：

```java
@DistributedLock(
        key = "'tenant-member:' + #tenantId + ':' + #principalId",
        waitTimeSeconds = 2)
```

## 4. 限制请求频率

文件签名接口每个客户端地址每分钟最多使用 300 个令牌：

```java
@PostMapping
@RateLimit(
        types = RateLimitType.IP,
        capacity = 300,
        refillTokens = 300,
        timeWindowSeconds = 60)
public ApiResponse<FileSignatureView> sign(
        @Valid @RequestBody FileSignatureRequest request) {
    FileSignatureResult result = fileSignatureService.sign(
            request.getItems(), CurrentRequestHolder.current());
    return ApiResponse.ok(FileSignatureView.from(result));
}
```

可以同时使用多个维度：

```java
@RateLimit(
        types = {RateLimitType.IP, RateLimitType.USER},
        capacity = 20,
        refillTokens = 20,
        timeWindowSeconds = 60)
```

超过限制时返回 429 和 `Retry-After`。匿名接口不能只使用 USER；STOMP SEND 使用 CONNECTION 或 USER。

## 5. 记录操作日志

已有路径 ID 的接口直接使用 SpEL：

```java
@PutMapping("/{menuId}")
@Audit(
        module = "system.menus",
        action = "update",
        targetType = SystemResourceTypes.MENU,
        targetId = "#menuId")
@RequirePermission(SystemPermissions.MENUS_MANAGE)
public ApiResponse<Void> update(
        @PathVariable Long menuId,
        @Valid @RequestBody MenuUpdateRequest request) {
    menuService.update(
            menuId,
            request.getParentId(),
            request.getRouteCode(),
            request.getTitles(),
            request.getIcon(),
            request.getVisible(),
            request.getVersion());
    return ApiResponse.ok();
}
```

创建接口从返回值读取目标 ID：

```java
@PostMapping
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

## 6. 选择审计写入模式

| `AuditMode` | 行为 | 使用情况 |
| --- | --- | --- |
| `DEFAULT` | 使用 `SCA4DJ_AUDIT_MODE` | 普通操作 |
| `ASYNC` | 请求结束后由审计线程写入 | 操作日志失败不应回滚业务 |
| `SYNC` | 与业务使用同一数据库事务 | 没有操作日志就不能提交的业务 |

单个接口可以覆盖全局配置：

```java
@Audit(
        module = "security.credentials",
        action = "change-password",
        mode = AuditMode.SYNC,
        targetType = "credential",
        targetId = "#credentialId")
```

ASYNC 使用有界队列。队列已满或写入失败时记录错误日志，但不改变业务响应。应用关闭时最多等待 `SCA4DJ_AUDIT_ASYNC_SHUTDOWN_AWAIT`。

## 7. 添加 Audit Payload

简单详情可以由表达式生成字符串键 Map：

```java
@Audit(
        module = "platform.roles",
        action = "replace-permissions",
        targetType = "role",
        targetId = "#roleId",
        payload = "{'roleId': #roleId, "
                + "'permissionCount': #request.permissionCodes.size()}")
public ApiResponse<Void> replacePermissions(
        @PathVariable Long roleId,
        @Valid @RequestBody RolePermissionReplaceRequest request) {
    roleService.replacePermissions(
            roleId, request.getPermissionCodes(), request.getVersion());
    return ApiResponse.ok();
}
```

Map 使用内置 `raw` 类型和版本 `1` 保存。需要长期读取的固定 JSON 结构使用 `AuditPayload` 和 `AuditPayloadCodec`；接口定义见[审计参考](/reference/interfaces-annotations.md#审计)。Payload 不得包含密码、token、Authorization Header、签名密钥或不必要的大段正文。

## 8. 运行结果

- 两个请求使用同一 version 时只有一个更新成功，另一个返回 409；
- 同一幂等 key 的重试不会创建第二条记录；
- 同一锁 key 在两个应用实例中不会同时执行；
- 超过限流容量时返回 429；
- 成功和失败操作按配置写入 `sys_log`；
- 创建操作日志的 targetId 等于新记录 ID；
- SYNC 审计失败会回滚业务事务，ASYNC 审计失败不会改变业务结果。

## 失败处理

测试失败时先检查注解是否落在 Spring 代理调用的方法上，再检查 Request 是否实现对应接口、SpEL 参数名是否存在、Redis 连接以及 Repository 的版本条件。审计记录缺失时同时检查操作目录、`SCA4DJ_AUDIT_MODE` 和应用日志中的异步写入错误。

## 下一步

写入成功后需要通知其他进程或在线页面时，继续[发布消息和实时更新](./messaging-and-realtime.md)。
