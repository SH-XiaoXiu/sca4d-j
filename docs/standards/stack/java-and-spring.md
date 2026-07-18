# Java 与 Spring 规范

## Java 类型安全

业务状态使用枚举，稳定组合使用业务类型，泛型保留领域约束。禁止以 `Map<String, Object>`、裸字符串或反射绕过可编译契约。

## Nullability 契约

接口、实现和覆盖方法必须使用一致的 JetBrains `@NotNull`/`@Nullable`。集合优先返回空集合；找不到、无权限和异常必须使用不同结果或异常表达。

## Service 接口与实现

存在两个实现，或调用方需要依赖一个长期不变的公共方法集合时，才创建 Service 接口。实现类使用业务名称；事务注解放在可被 Spring 代理的公共方法上。

## 私有方法分类

仅保留独立业务判断、复杂算法、复用逻辑和框架回调。所有 private 方法写中文文档注释；仅表示执行步骤的方法必须内联。

## Spring 原生机制

配置绑定、校验、事务、事件和生命周期优先使用 Spring 原生机制。不得再包装一层无语义 Manager/Helper。跨 Bean 调用才能依赖代理语义，禁止自调用假设 `@Transactional`、`@Async` 或 AOP 生效。

## Sa-Token 原生机制

登录、Session 和 RBAC 使用 Sa-Token 正式 API。当前 Principal 保持请求局部，不写 token Session；权限编码不接受通配；业务资源范围不塞入 Sa-Token 权限列表。

## Jackson 与枚举转换

统一 ObjectMapper 配置、BaseEnum codec 和 View serializer。业务代码不得自行创建 ObjectMapper 绕过模块；未知枚举值失败关闭，View serializer 不查询数据库。

## 异常类型

业务失败使用 `BizException` 和稳定错误码；内部依赖失败使用 `InternalServiceException`；外部系统失败使用 `ExternalServiceException`。Controller 不捕获后统一返回成功，底层异常文本不直接暴露。

## 配置绑定

脚手架配置使用类型化 `Sca4djXxxProperties`，带默认值、校验和中文 JavaDoc。在归属 Config 显式启用，禁止在业务代码散落 `@Value` 字符串。

## 基础设施显式代码

锁、消息、文件、审计等关键设施允许保留较长的显式实现。不得用元编程或通用模板隐藏事务、重试和失败语义。

## 完整审查与日志

Java 文件的分层、类型、私有方法、框架生命周期和集群成立性执行[Java 工程完整审查规范](./java-engineering-review)。业务与基础设施日志执行[应用日志规范](/standards/facilities/logging)，访问日志不能代替业务阶段日志。
