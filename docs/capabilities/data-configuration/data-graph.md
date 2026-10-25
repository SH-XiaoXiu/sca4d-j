# DataGraph

源码：`core/sca4dj-core-api/src/main/java/com/sca4d/core/api/data/DataGraph.java`

本页说明 DataGraph 的构建规则、结构校验，以及执行器负责的范围。本页适用于需要声明多个数据操作之间依赖关系的开发者。读完后，开发者能判断 Graph 定义在何种情况下被拒绝，以及执行器负责哪些事情。

## 核心概念

DataGraph 保存已经完成结构校验的数据操作有向无环图（DAG）。Graph 输入由调用方提供。每个 [DataOperation](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/data/DataOperation.html) 读取已声明的输入，并产生唯一输出。某个节点的输出，可以成为后续节点的输入。

`Builder.build()` 按节点登记顺序执行稳定拓扑排序。两个节点同时可以执行时，先登记的节点排在前面。因此，相同 Graph 每次得到相同顺序。建成后的 `inputs()` 和 `operations()` 都不可修改。

## 构建示例

节点可以先登记，后满足依赖。`build()` 会把它们排成正确顺序：

```java
DataKey<String> source = DataKey.of("source", String.class);
DataKey<Integer> length = DataKey.of("length", Integer.class);
DataKey<String> label = DataKey.of("label", String.class);

DataOperation<String> buildLabel = DataOperation.of(
        "build-label", List.of(length), label,
        values -> "length=" + values.get(length));
DataOperation<Integer> measure = DataOperation.of(
        "measure", List.of(source), length,
        values -> values.get(source).length());

DataGraph graph = DataGraph.builder()
        .input(source)
        .operation(buildLabel)
        .operation(measure)
        .build();

// graph.operations() 的顺序是 measure、buildLabel。
```

## 结构校验

`build()` 在任何节点执行前，拒绝以下结构：

- Graph 输入键或节点输出键名称重复；
- 节点名称重复；
- 节点引用 Graph 未声明的键；
- 节点使用同名但类型不同的键；
- 同一节点重复声明输入；
- 节点之间存在依赖环。

这些失败都抛出 IllegalArgumentException。它说明 Graph 定义本身有错误，不属于一次输入数据的失败。

依赖关系在构建阶段校验，而不是在执行阶段才发现错误。

## 适用范围

DataGraph 只描述数据依赖，不执行节点。它不依赖 Spring、Jackson、View、Pipeline 或 Job。

[DataGraphExecutor](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/data/DataGraphExecutor.html) 可以同步计算完整图。它也可以通过 `executeAsync(graph, inputs, executor)` 并行调度已经具备输入的节点。依赖节点完成后，才启动后续节点。

异步结果只在全部节点成功时返回完整 DataValues。任一节点失败或调用方取消时，执行器会尝试取消未完成的节点。取消不能保证中断已经运行的 Java 函数、RPC 或数据库请求。

Graph 不传播调用线程上的事务或上下文绑定。执行器由调用方管理。

DNF 的异步 Clause 当前把互相独立的条件，作为 Graph 节点并行计算。等全部条件完成后，汇总布尔结果。某个条件返回 `false` 不会取消其他条件。读取外部系统的条件，必须由调用方设置超时。

同步 `matches()` 按声明顺序短路。由 `DnfClause.Condition.async(...)` 创建的条件，只能通过 `matchesAsync(...)` 调用。同步入口触达该条件时，抛出异常。

## 执行示例

以下示例执行上文构建的 `graph`。同步入口 `DataGraphExecutor.execute` 要求输入与 Graph 声明的输入完全一致：

```java
DataValues inputs = DataValues.builder()
        .put(source, "workspace-handbook")
        .build();

DataValues result = DataGraphExecutor.execute(graph, inputs);
String text = result.get(label);
// text 为 "length=18"
```

并行场景使用 `executeAsync`，执行器由调用方提供：

```java
CompletableFuture<DataValues> future = DataGraphExecutor.executeAsync(graph, inputs, executor);
```

`execute` 在输入与 Graph 不一致时抛出 DataAssertionException。节点抛出其他运行时异常时，抛出 DataOperationExecutionException，原异常保留为 cause。两种失败都不返回部分结果。
