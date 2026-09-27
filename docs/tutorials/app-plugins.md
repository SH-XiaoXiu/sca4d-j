# 为 App 开放插件 API

App 声明宿主服务和扩展契约后，脚手架在构建时生成类型安全客户端、直接调用分发器和可发布的 API JAR。本教程以 Sca4d Server 的多因素认证（MFA）策略插件为例，说明如何声明、绑定、构建、加载和热替换插件。

插件针对 App 生成的 API 编程。认证、IAM 和 IoT 等业务模块继续使用普通接口；App 把生成客户端挂到自己的业务位置。

## 声明与绑定

`@PluginService` 声明宿主服务，`@PluginExtension` 声明插件可以提供的实现。每个公开方法通过 `@PluginOperation` 指定稳定操作编码。运行时服务、操作、扩展和插件编码使用 TreeCode。

Sca4d Server 的 `MfaFacts` 使用 `AUTH:MFA:FACTS`，提供账号设置、已安装方法元数据和可用方法，不提供 Credential、Proof 或 Secret。`MfaPolicy` 使用必需的单实现扩展 `AUTH:MFA:POLICY`，决定因素要求、允许的方法、设置合法性和凭据移除保护。

已有类也可以声明 `@PluginService`，只公开带 `@PluginOperation` 的 public 实例方法。构建生成独立接口及宿主绑定类。需要隐藏内部类型或改变签名时，由 App 编写适配对象，再通过 `@PluginBinding` 绑定接口。

```java
@Service
@PluginBinding(MfaFacts.class)
public class MfaFactsService implements MfaFacts {
    // 使用宿主已有 Settings 和认证方法读取事实。
}
```

App 数据类型通过 `@PluginApiType` 导出。它们的字段及公开方法引用的外部类型必须来自明确的共享构件。生成器拒绝任意 Object、没有具体类型的泛型、Future、流和函数对象等公开签名。

## 构建 App API

App 使用 `com.sca4dj.plugin-api` 配置生成入口：

```kotlin
appPluginApi {
    id.set("SCA4D:SERVER")
    version.set("1.0.0")
    generatedPackage.set("com.sca4d.app.server.plugin.api")
    entryName.set("Sca4dServerPlugin")
}
```

`pluginShared` 明确选择共享构件。示例共享整个 `sca4dj-core-api`，因此插件可以使用 TreeCode、认证因素及 Plugin 公共契约。共享范围不从宿主全部依赖推断，也不自动包含 Core Spring 或具体认证模块。

`pluginProcessor` 配置注解处理器。仓库内 App 将它绑定到 Included Build 的 `pluginProcessorJar`；独立工程使用发布的 `com.sca4dj:sca4dj-plugin-processor`。

```bash
nice -n 10 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=384m" :apps:sca4d-server:pluginApiJar :apps:sca4d-server:pluginApiSourcesJar
```

输出位于 App 的 `build/libs`，仅包含公开接口、显式数据类型、生成客户端、App 插件入口和契约描述，不包含宿主 Service、Repository、Mapper 或 Controller。

生成源码位于 `build/generated/sources/annotationProcessor/java/main`。Gradle 向 IDEA 声明该目录；修改声明后重新编译，不手工编辑生成文件。

`publishPluginApiPublicationToSca4djRepository` 把 API、源码和共享依赖 POM 发布到根 `build/repository`。示例坐标为 `com.sca4dj:sca4d-server-plugin-api:1.0.0`。API 版本独立于 App 构建版本。

## 编写插件

插件使用 `com.sca4dj.plugin`，通过 `pluginApi` 依赖生成 API。仓库内选择 App 的 `pluginApiElements`，独立工程使用发布坐标。该配置用于编译和测试，不打入插件运行 JAR。

```kotlin
dependencies {
    add("pluginApi", project(path = ":apps:sca4d-server", configuration = "pluginApiElements"))
}

sca4djPlugin {
    id.set("SCA4D:SERVER:MFA")
    entryClass.set("com.sca4d.app.mfa.MfaPolicyPlugin")
    api.set("SCA4D:SERVER")
    requiresApi.set(">=1.0.0 & <2.0.0")
}
```

插件继承 App 生成的基类，通过类型安全上下文取得宿主服务并登记扩展：

```java
public final class MfaPolicyPlugin extends Sca4dServerPlugin {
    @Override
    public void onLoad(Sca4dServerPluginContext context) {
        context.registerMfaPolicy(new DefaultMfaPolicy(context.mfaFacts()));
    }
}
```

`onLoad` 准备候选实现。初始化、版本校验和绑定冲突检查成功后，运行时一次发布全部登记结果。候选失败时，运行时尝试停止候选并关闭加载资源，旧版继续接受调用。初始化期间不要执行无法撤销的外部业务动作。

私有依赖放入 `implementation`。`pluginJar` 生成普通 JAR，拒绝重复私有类或重复打包共享类型。服务描述文件冲突也会使构建失败，插件作者需要明确处理资源，不能依赖覆盖顺序。Boot 可执行 JAR 和多版本类不作为插件交付形式。

## 挂载与调用

App 在构造参数或字段上声明 `@PluginMount`：

```java
public PlatformAuthenticationPolicyService(
        Settings settings,
        @PluginMount("AUTH:MFA:POLICY") MfaPolicy policy) {
    this.settings = settings;
    this.policy = policy;
}
```

KEYED 扩展同时指定 `key`。挂载得到生成客户端，插件替换时客户端对象不变。

客户端通过 ApiInvoker 调用已登记操作，分发器直接调用实际对象。宿主绑定使用 Spring 代理后的 Bean，保持当前线程和 Call，不自动改变身份、事务管理器或数据源。动态入口继续检查目标、参数、结果和共享类型身份。

`@PluginScope` 标记 App 类、方法或 `@Bean` 方法。作用域保存进入时的绑定快照，使一段同步操作内的多次调用使用同一组插件代次，嵌套范围复用快照。外层 Spring 事务已开启事务同步时，租用延续至提交或回滚完成。租用表示旧代次仍被调用持有，使用数量归零后才能释放。

首版只管理同步调用。插件实现对象、私有依赖对象和私有 Class 不得逃出调用范围；后台线程、订阅和异步结果需要另外管理，不能依靠普通方法返回自动释放。

## 部署 MFA 插件

Sca4d Server 的 `bootRun` 自动先构建 MFA JAR 并配置实际路径。部署构件使用：

```bash
nice -n 10 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=384m" :apps:sca4d-server:pluginDistribution
```

发行 ZIP 包含宿主 Boot JAR 和 `plugins/sca4d-server-mfa.jar`。解压后从发行目录启动宿主，或设置 `SCA4DJ_MFA_PLUGIN` 指向外部构件。

| 配置 | 作用与默认值 |
| --- | --- |
| `sca4dj.plugin.enabled` | 显式设置为 true 时启用 Spring 适配；Sca4d Server 已启用 |
| `sca4dj.plugin.api` | 启用时必须指定，与生成描述中的 App API 编码一致 |
| `sca4dj.plugin.artifacts` | 启动时加载的本地 JAR 路径列表，默认空列表；必需扩展仍须有实现 |
| `sca4dj.plugin.cache-directory` | 保存构件不可变副本，默认 `build/plugin-cache` |
| `sca4dj.plugin.drain-timeout` | 等待旧代次操作结束的非负时长，默认 `PT30S`；零表示不等待 |
| `SCA4DJ_MFA_PLUGIN` | Sca4d Server 的必需 MFA 插件路径，默认 `plugins/sca4d-server-mfa.jar` |
| `SCA4DJ_PLUGIN_CACHE` | Sca4d Server 的插件缓存目录，默认 `build/plugin-cache` |

Spring 在单例、数据库迁移和系统身份准备完成后加载插件，再开放正常业务。必需扩展缺失、API 不兼容或初始化失败使启动失败，不回退到较弱认证策略。

## 热替换与停止

App 注入 Core 的 PluginRuntime，通过 `replace(Path, Duration)` 加载同编码新版本。PluginUpdate 分别报告新旧代次状态。

新代次发布后接受新调用，已有作用域继续使用旧代次。等待超时或线程中断时，新版已经生效，旧版保留 DRAINING 状态，并在最后一个租用归还后尝试释放。停止失败记录 FAILED，不强行终止线程。状态查询包含版本、代次、构件摘要、使用数量和阶段，不持有实际插件对象。

普通 `stop` 不允许移除必需扩展。MFA 通过替换更新或随 App 关闭；回退通过重新加载上一份构件完成。

宿主共享明确的 API 类，每个插件代次独立加载实现与私有依赖。私有类缺失时不借用宿主或其他插件依赖。类加载隔离不是恶意代码的安全沙箱；关闭加载器释放文件资源，类卸载由 JVM 在引用消失后执行。

MFA attempt 不保存插件对象或代次。每次开始、提交和消费操作内部固定版本，下次操作重新检查当前策略。账号设置、Credential、Session 和 IAM 数据保持原归属和存储格式。
