# 权限规则

## 权限目录

所有功能权限必须进入 `PermissionCatalog`，使用 `<domain>:<resources>:<action>` 明确编码。禁止 `*` 通配、散落字符串和未登记权限。

## 功能与资源分离

`@RequirePermission` 只判断是否允许执行某类操作；`@Access`、Policy 和 Criteria 处理资源范围。禁止把租户 ID 拼进权限码，禁止把业务数据范围塞入 SessionPermission。

## Policy

每个领域 Policy 必须明确 PLATFORM、TENANT、SYSTEM 和不支持的 Principal，返回类型安全 Criteria 或拒绝。异常、未知类型、空输入和空集合必须失败关闭。

## Repository 查询条件

所有受范围保护的读写必须由 Repository 将 Criteria 放入 SQL。禁止查询后在内存过滤，禁止 Controller 做一遍检查后执行无范围写入。

## 领域访问范围

范围模型必须由具体领域拥有并显式失败关闭。租户领域使用 PLATFORM Principal 专用 ACL，按 `READ/WRITE/MANAGE` 保存动作上限；缺少默认规则且授权集合为空只能表示无范围。范围修改必须更新版本并触发客户端刷新。

## 前端权限

前端权限只控制按钮和页面是否显示。后端每次请求仍必须检查登录状态、选择 Principal，并检查功能权限和数据范围。
