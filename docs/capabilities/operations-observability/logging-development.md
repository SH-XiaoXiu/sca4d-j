# 应用日志规范

本页说明应用日志的开发规则，包括 HTTP 请求日志、日志来源、业务阶段、级别、消息格式、事务与异步结果、敏感数据和 Trace 。

## 目标

日志必须包含发生事件的模块、当前业务对象与结果，以及定位或恢复所需的信息。

统一访问日志只记录 HTTP 请求的方法、路径、状态和耗时。它不能代替记录业务状态变化、调度结果、消息投递、缓存处理和外部依赖结果的应用日志。操作审计记录操作人、对象和动作，也不能代替排障日志。

## HTTP 请求日志

Core Spring 的 HttpCallFilter 在普通 HTTP 请求完成时输出一条 `INFO` 访问日志。处理耗时达到 `sca4dj.web.slow-request-threshold` 时，日志级别提升为 `WARN`。默认阈值为 `200ms`，配置作用于整个 Servlet 应用，修改后需要重启应用。该配置不改变请求超时、响应状态或业务结果，也不创建异常审计记录。

```yaml
sca4dj:
  web:
    slow-request-threshold: 200ms
```

阈值必须至少为 1 毫秒，且可表示为 Java `long` 纳秒数。非法值会使启动配置绑定失败。计时使用单调时钟，不受系统时间校准影响。

日志记录调用标识、 HTTP 方法、不含查询参数的路径、记录时的响应状态、处理耗时和请求链失败标记。慢请求日志还会记录阈值。日志不记录请求体、查询参数、认证 Header 或重复异常堆栈。同步请求中的 `failed=true` 表示异常离开了请求链，此时状态码可能尚未经过容器错误处理。由现有异常处理器生成的错误响应仍以响应状态表示结果。

```text
HTTP 请求完成 | callId=example-call method=GET path=/api/workspaces/1/exports status=200 durationMs=35 failed=false
```

慢请求会额外包含阈值字段：

```text
慢 HTTP 请求 | callId=example-call method=GET path=/api/workspaces/1/exports status=200 durationMs=350 thresholdMs=200 failed=false
```

同步请求在过滤器链返回时检查。 Servlet 异步请求在 `onComplete` 时检查，包含异步等待及结果处理时间。超时回调先由原有处理器决定响应，再在完成时读取状态。一个请求跨多次异步周期也只记录一次，完成回调不会把请求的 Call 或 MDC 留在其他线程。

响应类型为 `text/event-stream` 的 SSE 和状态码为 `101` 的协议升级不输出普通请求日志。只在请求头中声明接受 SSE 不会跳过检查，例如返回 JSON 的慢速错误响应仍会被记录。这一耗时不包括 Gateway 转发前的等待和浏览器渲染，也不替代数据库原生慢 SQL 日志。

## 日志来源

业务类使用默认 SLF4J 类 logger：

```java
@Slf4j
public class FileCleanupService {
}
```

手写 logger 时必须传入当前类：

```java
private static final Logger log = LoggerFactory.getLogger(QuartzJobScheduler.class);
```

禁止使用自定义 topic 隐藏实际类名，禁止在每条消息中手写模块字符串。 `LogSourceFormatter` 从完整 logger 名生成来源。它只处理以 `com.sca4d` 开头的 logger 。转换结果如下：

```text
com.sca4d.storage.file.lifecycle.FileCleanupService
-> module=storage class=FileCleanupService

com.sca4d.core.spring.web.HttpCallFilter
-> module=core.spring class=HttpCallFilter

com.example.external.Client
-> logger=com.example.external.Client
```

`com.sca4d` 后的第一个包段作为模块名。 `core` 下取两个包段，例如 `core.spring`。类名取最后一个包段。根包下直接的类使用 `module=server`。非根包内的 logger 原样输出完整名称。

## 业务阶段

一个完整业务流程根据实际发生的阶段记录日志，而不是机械记录每次方法进入和返回。至少检查以下阶段：

1. 开始处理具有独立运维意义的任务、批次或外部事件；
2. 做出会改变后续结果的关键业务决策；
3. 完成状态变化、持久化或资源替换；
4. 发起消息、通知、文件、缓存和外部系统副作用；
5. 得到最终成功、部分成功、跳过、重试或失败结果。

一次简单同步查询不必打印开始和完成两条日志。后台批次、调度任务、消息消费、跨系统同步和不可见异步流程必须留下开始、关键进展和最终结果。

日志描述观察者看到的业务或技术事件，不描述代码正在调用哪个方法。

错误写法：

```java
log.info("开始执行 cleanup 方法");
log.debug("进入 if 分支");
```

正确写法：

```java
log.info("附件清理批次开始 | claimedCount={} batchSize={}", claimedCount, batchSize);
log.debug("附件清理任务跳过，已由其他节点处理 | taskId={} version={}", taskId, version);
log.info("附件清理批次完成 | successCount={} retryCount={} durationMs={}", successCount, retryCount, durationMs);
```

## 级别选择

### INFO

记录生产环境默认应看到的业务事实和设施生命周期：

- 业务任务、批次、调度器和消费者开始或结束；
- 关键状态迁移及其稳定对象标识；
- 外部副作用的最终成功或明确接受结果；
- 批量操作的成功、跳过、重试和失败数量；
- 启动、关闭、主从切换、租约取得等运维事件。

高频消息、循环内每项成功、普通查询参数和内部路由细节不得进入 INFO 。

### DEBUG

记录定位决策和技术细节所需的信息：

- 分支选择、路由目标和过滤原因；
- 查询或批次的数量、范围和版本；
- 重试次数、退避时间、锁键摘要和租约版本；
- 外部请求目标、非敏感响应摘要和耗时；
- 缓存命中、失效原因和消息 header 摘要。

构造 DEBUG 参数需要昂贵序列化或遍历时，先使用 `log.isDebugEnabled()`。但不得因此把完整业务对象或敏感 Payload 写入日志。

### TRACE

框架级设施可以使用 TRACE 记录默认无需查看的高频内部细节，例如拦截器匹配、协议帧流转、缓存脚本结果和内部路由选择。 TRACE 只用于还原框架执行路径，不记录业务状态变化，不得代替业务流程应有的 INFO 或 DEBUG 。

TRACE 同样遵守字段格式和敏感数据要求。协议帧、请求、响应和消息只能记录类型、长度、结果与必要标识，不得输出完整内容。

框架源码中没有定义各环境的 Root Logger 级别、控制台与文件输出。具体应用的级别和输出文件由其 `logback-spring.xml` 决定。 TRACE 排查结束后，应把单独调整过的 logger 级别恢复为默认值。

### WARN

记录异常但仍可恢复、降级或等待重试的结果。消息必须说明当前影响和恢复方式，例如「保留数据库记录等待下次同步」「当前任务已由其他节点接管，本轮不写入结果」。正常的业务校验失败、未找到和无权限不应在多个层重复记录 WARN 。

### ERROR

记录当前流程无法自行恢复的失败、重试耗尽、数据可能不一致或关键设施不可用。异常对象放在参数最后，并只由负责最终处理该异常的代码位置记录一次。

底层记录异常后继续抛出时，上层再次记录同一堆栈会制造重复噪声。底层如果只是补充上下文并继续抛出，应由最终异常处理位置统一打印。底层吞掉异常、转换为重试或保留部分结果时，必须在当前层记录结果和异常。

## 消息格式

应用消息统一使用：

```text
中文事件描述 | key={} key={}
```

应用启动完成时面向开发者展示的多行启动横幅不属于业务事件日志，可以保留独立排版。启动检查、依赖故障和运行阶段变化仍使用上述单行格式。

字段顺序保持稳定：

1. 主要业务对象 ID 、任务 ID 或 messageId；
2. 业务状态、结果和版本；
3. 数量、批次、尝试次数和耗时；
4. 技术目标、 provider 、队列或节点；
5. 异常对象。

字段名称使用 camelCase 。相同概念在不同类中使用同一个名称，不混用 `id` 、 `bizId` 、 `target` 等模糊字段。

使用 SLF4J 占位符，禁止字符串拼接和 `toString()` 整对象输出：

```java
log.error(
        "调度计划同步失败，保留数据库记录等待下次同步 | scheduleId={} schedulerName={}",
        scheduleId,
        schedulerName,
        exception);
```

## 事务和异步结果

事务提交前只能记录「已校验」「已写入事务」「已登记 Outbox 」等当时已经成立的事实，不得记录「创建成功」「发布完成」。普通事务阶段使用 `DEBUG`。重要数据库结果依赖提交成功时，使用 `LogMark.AFTER_COMMIT` 记录 `INFO`。

应用在 `logback-spring.xml` 中注册 TransactionLogTurboFilter 。业务代码使用当前类的 SLF4J logger：

```java
LOG.atInfo()
        .addMarker(LogMark.AFTER_COMMIT)
        .log("附件已确认 | messageId={} attachmentId={}", messageId, attachmentId);
```

提交后日志的准入条件、参数限制和回调失败时的处理结果见[注释、文档注释与日志](/standards/08_comments-and-logging#事务提交后动作与日志)。提交后日志可能随进程退出而丢失，不能代替操作审计、 Outbox 或持久任务。

异步提交只表示任务已经接受，不表示异步工作成功。日志必须区分 `accepted` 、 `started` 、 `completed` 、 `retrying` 和 `failed` 对应的事件。

批量任务存在部分成功时，不使用一条笼统的「处理完成」。记录成功、跳过、重试、失败数量，并为失败项保留可关联的 taskId 、 messageId 或对象 ID 。

## Trace 与后台任务

HTTP 、 STOMP 和请求内消息复用现有 MDC 中的 callId 、 parentCallId 和 principalId，不在消息正文重复这些字段。

后台任务没有请求 traceId 时，不伪造请求关系。使用 scheduleId 、 executionId 、 taskId 、 messageId 、 batchId 和节点名等真实标识关联日志。异步线程不得隐式继承可能已经失效的请求 MDC 。

## 敏感数据

禁止记录：

- 密码、验证码、 Token 、 Authorization 和 Cookie；
- 私钥、签名密钥、完整签名 URL 和数据库凭据；
- 完整请求体、响应体、消息 Payload 和审计 Payload；
- 无排障必要的手机号、邮箱、证件号和文件内容；
- 可以直接恢复秘密或个人数据的序列化对象。

确需识别外部值时，记录内部 ID 、长度、类型、哈希摘要或经过确认的脱敏值。服务端 ERROR 日志必须记录原始异常、 message 、 cause 和完整堆栈，便于定位真实故障。 HTTP 响应仍只返回统一响应码和固定文案，不得暴露异常详情。秘密、 Token 和完整第三方响应不得被拼入异常消息，应在产生异常的位置控制。

示例应用的 `dev` Profile（`application-dev.yml`）通过 `mybatis.configuration.log-impl` 把 MyBatis SQL 日志交给 SLF4J 输出。该开发日志会包含 SQL 参数和结果摘要，只能连接本地开发数据。禁止在 staging 、 prod 或包含生产秘密和个人数据的环境启用。

## 修改时的检查

新增或修改业务流程时，必须同时检查整个流程的日志。改动已有日志类时，统一本页规定的事件表达、级别、字段和异常归属。未改动的模块不要求一次性重写，但不得复制已有的不统一格式。

以下任一情况阻塞提交：

- 业务或异步流程没有足够日志判断执行到哪个阶段；
- 只有 HTTP 访问日志，没有业务状态和副作用结果；
- INFO 被逐项循环、高频路由或内部变量淹没；
- ERROR 没有异常、结果和对象标识，或同一异常被重复打印；
- 日志记录事务或异步任务成功，但实际只完成受理或事务内写入；
- 日志包含秘密、完整 Payload 或不必要个人数据；
- 新增 Logger 包装层只是隐藏 SLF4J 调用，没有完成可以独立命名的日志操作。