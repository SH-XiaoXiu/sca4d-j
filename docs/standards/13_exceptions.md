# 13 异常与失败

异常类型决定调用方如何处理失败、客户端看到什么状态，以及运维从日志里能找到什么。本页说明失败怎样分类、异常怎样命名和放置、哪些地方可以捕获，以及业务失败怎样使用错误码。Java 类型与 Spring 装配的一般规则见 [04 Java 与 Spring](/standards/04_java-and-spring)。

## 失败分类

先判断失败属于哪一类，再选择异常类型。

| 失败类型 | 含义 | 使用方式 | 示例 |
| --- | --- | --- | --- |
| 业务拒绝 | 请求合法，但当前状态、权限或规则不允许 | `BusinessException` + 模块错误码，分类为 `CONFLICT` 或 `FORBIDDEN` | 订单已取消后不能再支付 |
| 资源不存在 | 指定的对象不存在或不可见 | `BusinessException` + `NOT_FOUND` | 查询的 Order 不存在 |
| 输入不合法 | 调用方提供的业务输入不满足规则 | `BusinessException` + `VALIDATION` | 订单明细数量为零 |
| 依赖或外部失败 | 数据库、消息、设备网关、第三方服务不可用或返回异常 | 模块自己的 `XxxException`，保留操作对象、目标和是否可重试 | 支付服务超时 |
| 配置错误 | 启动或装配时缺少或非法配置 | `IllegalStateException`，启动时直接失败 | 缺少必填的签名密钥配置 |
| 数据或不变量违反 | 数据库影响行数、持久化数据或内部约束不符合预期 | `AffectedRowsMismatchException` 或 `IllegalStateException`，记录错误日志 | 单行更新影响了两行 |
| 调用约定违反 | 调用方传入的参数违反方法约定，且不是业务输入 | `IllegalArgumentException`、`NullPointerException` | 公开方法收到 `null` 形参 |

业务输入错误不属于调用约定违反。即使调用方来自内部代码，只要规则由业务决定，也应使用 `BusinessException`。

## 基本规则

1. 业务失败必须携带稳定错误码。业务路径不得用 `IllegalArgumentException` 或 `IllegalStateException` 表达业务拒绝，否则 HTTP 层只能给出通用 400 或 500，调用方无法区分原因。
2. 错误码由模块声明为实现 `ErrorCode` 的枚举，并登记在该模块的号段内。错误码的分类决定 HTTP 状态，不在异常类里另行指定。
3. 能用 `BusinessException` 和错误码表达的失败，不新建异常类。
4. 异常类名以 `Exception` 结尾。不使用 `Failure`、`Fault`、`Wrapped*`、`*Holder` 等后缀或名称。`java.lang.Error` 及其子类只表示 JVM 级错误，项目代码不新建 `Error` 子类。
5. 新增技术异常继承 `RuntimeException`，不继承 `BusinessException`。技术异常不映射为 4xx；需要区分类型时，使用模块级基类，例如 `PaymentClientException`。
6. 异常类放在所属模块的 `exception` 子包中。错误码枚举保持现有位置，不随异常类迁移。
7. 包装异常时必须保留原始异常作为 cause。不得只保留 `getMessage()`。
8. 异常文案面向开发者，写明失败条件和涉及对象。HTTP 响应只返回错误码和国际化消息，不返回异常文本、SQL、凭据或第三方完整响应。
9. 业务拒绝的判断放在拥有该状态的领域对象或服务中，拒绝时抛出 `BusinessException`。例如 `Order` 的支付判断应抛出带 `CONFLICT` 错误码的 `BusinessException`，而不是 `IllegalStateException`。

## 捕获

捕获 `RuntimeException`、`Exception` 或 `Throwable` 只允许出现在以下位置：

- HTTP、WebSocket 和 SSE 的处理位置；
- 定时任务、队列消费者和调度回调；
- 连接登记、注销和回收回调；
- 外部系统适配器中的协议处理位置。

捕获异常后，必须选择以下一种处理方式，并在代码注释或文档中说明理由：

- 转换为明确的业务错误码或技术异常；
- 重试，并说明重试次数或停止条件；
- 降级，并说明返回值对调用方的影响；
- 记录后继续抛出，由上层统一处理。

规则如下：

- 不允许空 `catch` 块。
- 不把失败伪装成空结果、`null`、`false` 或成功。
- 忽略异常时，注释写明为什么可以忽略、后续由谁或什么机制恢复。
- 同一异常只在最终处理的位置记录一次。中间层继续抛出时不重复打印堆栈，日志规则见 [08 注释、文档注释与日志](/standards/08_comments-and-logging)。

## 映射与响应

- HTTP 层对 `BusinessException` 按错误码分类映射状态码。
- `IllegalArgumentException` 映射为 400 只是未被业务层处理时的回退结果，不能作为业务输入校验的主路径。
- 未分类的异常映射为 500 `INTERNAL`，并记录错误日志。
- 后台任务的失败状态写入任务记录或事件，包含失败原因码、是否可重试和恢复方式。

## 文档与测试

- 公开方法的 `@throws` 写明每种异常的准确条件，规则见 [08](/standards/08_comments-and-logging)。
- 新增错误码必须有至少一个测试覆盖其 HTTP 映射或调用方可见的结果。
- 改变既有异常类型的行为，需要同时更新测试和调用方的处理逻辑。
