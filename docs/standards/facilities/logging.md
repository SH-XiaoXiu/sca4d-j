# 应用日志规范

## 目标

日志必须包含发生事件的模块、当前业务对象与结果，以及定位或恢复所需的信息。

统一访问日志只记录 HTTP 请求的方法、路径、状态和耗时，不能代替包含业务状态变化、调度结果、消息投递、缓存处理和外部依赖结果的应用日志。操作审计记录操作人、对象和动作，也不能代替工程排障日志。

## 日志来源

业务类使用默认 SLF4J 类 logger：

```java
@Slf4j
public class DefaultTenantCleanupBatchService {
}
```

手写 logger 时必须传入当前类：

```java
private static final Logger log = LoggerFactory.getLogger(QuartzBootstrapCoordinator.class);
```

禁止使用自定义 topic 隐藏实际类名，禁止在每条消息中手写模块字符串。`LogSourceConverter` 从完整 logger 名生成来源：

```text
com.sca4d.iam.cleanup.service.DefaultTenantCleanupBatchService
-> module=iam class=DefaultTenantCleanupBatchService

com.sca4d.common.messaging.outbox.DefaultMessageOutboxDeliveryService
-> module=common.messaging class=DefaultMessageOutboxDeliveryService
```

普通项目包使用 `com.sca4d` 后第一个包段作为模块。`common` 下的稳定设施使用前两个包段，例如 `common.audit`、`common.messaging`、`common.concurrent`。Controller、config、entity、mapper、utils 等结构包输出其技术归属；业务阶段日志应由所属领域 Service 或设施实现记录，不在 Controller 中复制。

第三方 logger 原样输出完整名称。转换器只能读取 `ILoggingEvent.getLoggerName()`，不得读取 caller data、扫描线程栈或反射调用类。

## 业务阶段

一个完整业务流程根据实际发生的阶段记录日志，而不是机械记录每次方法进入和返回。至少检查：

1. 开始处理具有独立运维意义的任务、批次或外部事件；
2. 做出会改变后续结果的关键业务决策；
3. 完成状态变化、持久化或资源替换；
4. 发起消息、通知、文件、缓存和外部系统副作用；
5. 得到最终成功、部分成功、跳过、重试或失败结果。

一次简单同步查询不必打印开始和完成两条日志。后台批次、调度任务、消息消费、跨系统同步和不可见异步流程必须留下开始、关键进展和最终结果。

日志描述观察者看到的业务或技术事件，不描述代码正在调用哪个方法。

错误：

```java
log.info("开始执行 cleanup 方法");
log.debug("进入 if 分支");
```

正确：

```java
log.info("Subject 关系清理批次开始 | claimedCount={} batchSize={}", claimedCount, batchSize);
log.debug("Subject 清理任务跳过，已由其他节点处理 | taskId={} version={}", taskId, version);
log.info("Subject 关系清理批次完成 | successCount={} retryCount={} durationMs={}", successCount, retryCount, durationMs);
```

## 级别选择

### INFO

记录生产环境默认应看到的业务事实和设施生命周期：

- 业务任务、批次、调度器和消费者开始或结束；
- 关键状态迁移及其稳定对象标识；
- 外部副作用的最终成功或明确接受结果；
- 批量操作的成功、跳过、重试和失败数量；
- 启动、关闭、主从切换、租约取得等运维事件。

高频消息、循环内每项成功、普通查询参数和内部路由细节不得进入 INFO。

### DEBUG

记录定位决策和技术细节所需的信息：

- 分支选择、路由目标和过滤原因；
- 查询或批次的数量、边界和版本；
- 重试次数、退避时间、锁键摘要和租约版本；
- 外部请求目标、非敏感响应摘要和耗时；
- 缓存命中、失效原因和消息 header 摘要。

构造 DEBUG 参数需要昂贵序列化或遍历时，先使用 `log.isDebugEnabled()`，但不得因此把完整业务对象或敏感 Payload 写入日志。

### TRACE

框架级设施可以使用 TRACE 记录默认无需查看的高频内部细节，例如拦截器匹配、协议帧流转、缓存脚本结果和内部路由选择。TRACE 只用于还原框架执行路径，不记录业务状态变化，不得代替业务流程应有的 INFO 或 DEBUG。

TRACE 同样遵守字段格式和敏感数据要求。协议帧、请求、响应和消息只能记录类型、长度、结果与必要标识，不得输出完整内容。

服务端 `local` 和 `dev` 环境的 Root Logger 固定为 INFO，控制台输出 INFO 及以上，项目代码的 TRACE 及以上另行写入文件；安全过滤后的 MyBatis 预编译 SQL 可以单独以 DEBUG 输出。`prod` 环境控制台输出 INFO 及以上，文件记录 DEBUG 及以上。其他环境默认只在控制台输出 INFO 及以上。TRACE 排查结束后，应把单独调整过的 logger 级别恢复为默认值。

### WARN

记录异常但仍可恢复、降级或等待重试的结果。消息必须说明当前影响和恢复方式，例如“保留数据库记录等待下次同步”“当前任务已由其他节点接管，本轮不写入结果”。正常的业务校验失败、未找到和无权限不应在多个层重复记录 WARN。

### ERROR

记录超出当前流程恢复能力的失败、重试耗尽、数据可能不一致或关键设施不可用。异常对象放在参数最后并只由拥有最终处理责任的边界记录一次。

底层记录异常后继续抛出，上层再次记录同一堆栈会制造重复噪声。底层如果只是补充上下文并继续抛出，应由最终异常处理边界统一打印；底层吞掉异常、转换为重试或保留部分结果时，必须在当前层记录结果和异常。

## 消息格式

应用消息统一使用：

```text
中文事件描述 | key={} key={}
```

应用启动完成时面向开发者展示的多行启动横幅不属于业务事件日志，可以保留独立排版。启动检查、依赖故障和运行阶段变化仍使用上述单行格式。

字段顺序保持稳定：

1. 主要业务对象 ID、任务 ID 或 messageId；
2. 业务状态、结果和版本；
3. 数量、批次、尝试次数和耗时；
4. 技术目标、provider、队列或节点；
5. 异常对象。

字段名称使用 camelCase。相同概念在不同类中使用同一个名称，不混用 `id`、`bizId`、`target` 等模糊字段。

使用 SLF4J 占位符，禁止字符串拼接和 `toString()` 整对象输出：

```java
log.error(
        "Quartz 计划同步失败，保留数据库记录等待下次同步 | scheduleId={} schedulerName={}",
        scheduleId,
        schedulerName,
        exception);
```

## 事务和异步结果

事务提交前只能记录“已校验”“已写入事务”“已登记 Outbox”等当时已经成立的事实，不得记录“创建成功”“发布完成”。最终成功依赖事务提交时，在提交后回调、消费结果或能够确认提交的边界记录。

异步提交只表示任务已经接受，不表示异步工作成功。日志必须区分 `accepted`、`started`、`completed`、`retrying` 和 `failed` 对应的中文事件。

批量任务存在部分成功时，不使用一条笼统的“处理完成”。记录成功、跳过、重试、失败数量，并为失败项保留可关联的 taskId、messageId 或对象 ID。

## Trace 与后台任务

HTTP、STOMP 和请求内消息复用现有 MDC 中的 traceId、subjectId、principalType 和 principalId，不在消息正文重复这些字段。

后台任务没有请求 traceId 时，不伪造请求关系。使用 scheduleId、executionId、taskId、messageId、batchId 和节点名等真实标识关联日志。异步线程不得隐式继承可能已经失效的请求 MDC。

## 敏感数据

禁止记录：

- 密码、验证码、Token、Authorization 和 Cookie；
- 私钥、签名密钥、完整签名 URL 和数据库凭据；
- 完整请求体、响应体、消息 Payload 和审计 Payload；
- 无排障必要的手机号、邮箱、证件号和文件内容；
- 可以直接恢复秘密或个人数据的序列化对象。

确需识别外部值时记录内部 ID、长度、类型、哈希摘要或经过确认的脱敏值。服务端 ERROR 日志必须记录原始异常、message、cause 和完整堆栈，便于定位真实故障；HTTP 响应仍只返回统一响应码和固定文案，不得暴露异常详情。秘密、Token 和完整第三方响应不得被拼入异常消息，应在产生异常的边界控制。

MyBatis-Flex 在 `local` 和 `dev` Profile 通过 `configuration.log-impl` 启用控制台 SQL，其他环境的 SLF4J Mapper logger 固定为 INFO。该开发日志会包含 SQL 参数和结果摘要，只能连接本地开发数据，禁止在 staging、prod 或包含生产秘密和个人数据的环境启用。

## 触及即整改

新增或修改业务流程时，必须同时检查该完整流程的日志。改动已有日志类时，统一本文件规定的事件表达、级别、字段和异常归属。未触及模块不要求一次性重写，但不得复制已有的不统一格式。

以下任一情况阻塞提交：

- 业务或异步流程没有足够日志判断执行到哪个阶段；
- 只有 HTTP 访问日志，没有业务状态和副作用结果；
- INFO 被逐项循环、高频路由或内部变量淹没；
- ERROR 没有异常、结果和对象标识，或同一异常被重复打印；
- 日志记录事务或异步任务成功，但实际只完成受理或事务内写入；
- 日志包含秘密、完整 Payload 或不必要个人数据；
- 新增 Logger 包装层只是隐藏 SLF4J 调用，没有形成独立日志能力。
