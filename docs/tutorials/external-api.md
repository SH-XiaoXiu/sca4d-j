# 开放 API

本页以 Workspace 为例。
第三方程序需要读取某个 Workspace 中的 Document，例如把 Document 同步到外部知识库。
该程序没有 Member 的登录 Session，也不应使用管理端接口。

开放 API 让第三方程序通过自己的 Client 凭据调用明确公开的业务接口。
应用先声明认证端点和 `@OpenApiRoute`，再创建 Client 并授予允许的 Operation。
调用方选择 Bearer Token 或 HMAC 签名，请求最终仍需通过 Principal 的权限检查。
每个 Client 使用独立的 APPLICATION Subject 和 Account，复用 IAM 的身份与授权关系。

读完本页后，可以完成以下工作：

- 声明认证端点，并开放一条读取 Document 的路由；
- 创建 Client，并交给第三方一次性凭据；
- 用 Token 或 HMAC 调用，并读取运行时 Swagger 文档。

前置条件：已理解 Operation 和 `@RequirePermission` 的用法，参见[接入身份与权限](/tutorials/identity-and-permission)。
开放路由同样要经过普通权限检查。

## 实现步骤

### 步骤 1：声明 Operation

开放路由引用 `OperationCatalog` 中已经注册的 Operation。
本页的 `DocumentOperations.READ` 是 Operation 编码常量，由具体应用定义，声明方式见[Operation 声明](/capabilities/identity-access/operation-declaration)。

### 步骤 2：声明认证端点

OpenAPI 模块实现 Client Credentials 的参数校验、Client 和 Account 状态检查、Shared Secret 认证、Session 建立与 Token 签发。
具体应用只需选择认证方式和 HTTP 路径：

```java
/** 声明开放 API 的 Token 端点，由具体应用定义。 */
@Configuration(proxyBeanMethods = false)
public class WorkspaceOpenApiConfiguration {

    @Bean
    OpenApiAuthenticationEndpoint openApiAuthenticationEndpoint() {
        return OpenApiAuthenticationEndpoint.clientCredentials("/openapi/oauth/token");
    }
}
```

这个 Bean 注册一个认证端点。
该端点接受 `POST application/x-www-form-urlencoded`，返回 JSON。

路径必须是非根绝对路径，不能以斜杠结尾，也不能包含重复斜杠、查询字符串或片段。
具体应用没有声明这个 Bean 时，模块不公开认证端点。

具体应用不编写 Token Controller，也不调用模块内部 Handler。
模块根据同一个 Bean 生成认证端点的运行时 Swagger 说明。
因此，修改 Bean 中的路径会同时修改实际路由和「开放 API」文档中的路径。

### 步骤 3：声明业务路由

`@OpenApiRoute` 是 Spring MVC 组合映射。
具体应用在自己的 Controller 方法上声明 HTTP 路由、Tool 标识和 Operation，并用 `@RequirePermission` 声明同一个 Operation。

```java
/** 向第三方程序开放 Document 读取，由具体应用定义。 */
@RestController
@Tag(name = "Document", description = "供第三方程序读取 Workspace 中的 Document。")
class DocumentOpenApiController {

    private final DocumentQueries documents;

    DocumentOpenApiController(DocumentQueries documents) {
        this.documents = documents;
    }

    @OpenApiRoute(
            method = RequestMethod.GET,
            path = "/openapi/workspaces/{workspaceId}/documents/{documentId}",
            toolGroup = "workspace",
            toolName = "getDocument",
            operation = DocumentOperations.READ)
    @Operation(
            summary = "查询 Document",
            description = "按 Workspace 和 Document 标识读取标题、正文摘要、作者 Member 和更新时间。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "找到 Document 并返回当前内容",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "document",
                                    value = "{\"code\":\"0\",\"data\":{\"id\":\"9001\"}}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Document 标识不存在",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/OpenApiError")))
    })
    @RequirePermission(operation = DocumentOperations.READ)
    ApiResponse<OpenApiDocumentResponse> get(
            @Parameter(
                            description = "Workspace 的十进制标识。",
                            required = true,
                            schema = @Schema(type = "string", example = "42", pattern = "^[1-9][0-9]*$"))
                    @PathVariable
                    long workspaceId,
            @Parameter(
                            description = "Document 的十进制标识。",
                            required = true,
                            schema = @Schema(type = "string", example = "9001", pattern = "^[1-9][0-9]*$"))
                    @PathVariable
                    long documentId) {
        return ApiResponse.ok(OpenApiDocumentResponse.from(documents.byId(workspaceId, documentId)));
    }
}
```

`ApiResponse` 指 Core Spring 的 `com.sca4d.core.spring.web.ApiResponse`。
`DocumentQueries` 和 `OpenApiDocumentResponse` 由具体应用定义。

`@OpenApiRoute` 的属性如下：

- `path` 必须以 `sca4dj.openapi.path-prefix` 加 `/` 开头，默认前缀是 `/openapi`。每个声明只能包含一个路径和一个 HTTP 方法。
- `consumes` 声明请求体媒体类型，没有请求体时保持为空。`produces` 默认只产生 `application/json`。
- `toolGroup` 和 `toolName` 是 Tool 使用的稳定标识。运行时文档分别写入 `x-tool-group` 和 `x-tool-name`。Springdoc 根据它们生成标准 OpenAPI `operationId`，例如 `workspaceGetDocument`。
- `operation` 是 `OperationCatalog` 中的权限操作编码，必须与 `@RequirePermission.operation` 完全相同。

Spring MVC 直接注册这个组合映射。
OpenAPI 模块在单例初始化完成后扫描 `@RestController`，校验路径、Tool 标识、Operation 是否存在，以及权限注解是否一致。
校验失败会阻止应用启动。
没有 `@OpenApiRoute` 的普通 Controller 方法不会进入开放 API 分组。

### 步骤 4：分开请求与响应类型

开放 API 的请求和响应类型应当与管理端、页面或内部业务接口的 HTTP 类型分开。
具体应用可以读取同一个 Service 结果，但要在 Controller 中转换为专门的开放类型。
例如，示例返回 `OpenApiDocumentResponse`，不复用管理端的 `DocumentView`。

每个开放方法同时完成以下声明：

- 用 `@Tag` 给 Swagger UI 提供清楚的业务分类和说明；
- 用 `@Operation` 说明动作、输入范围和结果，不在 summary 中重复 HTTP 方法；
- 用 `@Parameter`、Jakarta Validation 和 `@Schema` 声明路径、查询和请求体的类型、格式、范围与示例；
- 用 `@ApiResponses` 声明成功结果和该业务方法特有的失败结果，并提供可解析的 JSON 示例；
- 给开放请求、响应和枚举类型添加 `@Schema`，避免 Swagger 暴露内部类名或无法解释的字符串；
- 对可能超过 JavaScript 安全整数范围的 ID 和 version 使用十进制字符串，并在 schema 中声明正整数 pattern。

模块会为每个开放业务方法补充 `400`、`401`、`403` 和 `500` 的统一 `OpenApiError` 结构及示例。
具体方法继续声明 `404`、`409` 或其他由业务动作决定的失败结果。

### 步骤 5：创建 Client

应用管理代码调用 `OpenApiClientService.create(...)` 创建 Client：

```java
/** 为第三方知识库同步程序创建 Client，由具体应用定义。 */
@Service
public class DocumentSyncClientService {

    private final OpenApiClientService clients;

    public DocumentSyncClientService(OpenApiClientService clients) {
        this.clients = clients;
    }

    public OpenApiClientCredentials create(Session creator) {
        return clients.create(
                creator,
                "Document 同步程序",
                List.of(DocumentOperations.READ),
                "shared-secret");
    }
}
```

参数依次是创建者 Session、Client 管理名称、要开放的 Operation 和认证方法。
认证方法只能是 `shared-secret` 或 `hmac`。
`OpenApiClientCredentials` 提供 `client()`、`method()` 和 `secret()`。

服务会依次完成以下处理：

1. 创建 `SubjectType.APPLICATION` 的 Subject 和 `openapi` Realm 中的 Account；
2. 复制创建者 Session 中可选的 Principal 关系到这个 Account；
3. 只接受创建者当前拥有、且已经声明为开放路由的 Operation；
4. 保存 Client 与 Operation 的显式子集，并一次性返回认证凭据。

Client 的数据归 OpenAPI 模块负责，表为 `openapi_client` 和 `openapi_client_operation`。
凭据仍由 Shared Secret 或 HMAC 认证模块保存和校验。
明文只在签发或轮换响应中出现。
停用 Client 会同时阻止 Token 交换、HMAC 认证和后续 Operation 检查。
`OpenApiClientService` 还提供 `replaceOperations` 和 `changeStatus`。

### 步骤 6：使用 Token 调用

下面的请求假设具体应用把 Client Credentials 端点配置为 `/openapi/oauth/token`：

```http
POST /openapi/oauth/token
Authorization: Basic <base64(clientId:sharedSecret)>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```

成功结果包含 `access_token`、`token_type=Bearer`、剩余秒数和空格分隔的 Operation `scope`。
再用 Token 调用业务路由：

```http
GET /openapi/workspaces/42/documents/9001
Authorization: Bearer <access_token>
```

默认情况下，端点返回现有 Session access Token。
启用 `sca4dj.openapi.jwt.enabled=true` 后，模块改为使用独立 HS256 密钥签发 OpenAPI JWT。
JWT 配置还包括 `active-key-id`、`keys`、`issuer`、`audience`、`access-ttl`（默认 5 分钟）和 `max-token-length`（默认 6144）。
OpenAPI JWT 只在 `@OpenApiRoute` 路由中恢复，不改变浏览器和普通 HTTP 请求使用的 Token 模式。

### 步骤 7：使用 HMAC 签名调用

使用 HMAC Client 时，每个开放 API 请求都提交以下请求头。
下表是默认名称。
部署环境可以用 `sca4dj.openapi.hmac.headers.client-id`、`timestamp`、`nonce`、`signature` 分别配置名称。
`sca4dj.openapi.path-prefix` 默认为 `/openapi`，实际路由仍由每个 `@OpenApiRoute` 声明。
调用方与 Server 必须使用同一组启动时生效的名称和路径。

| 请求头 | 内容 |
| --- | --- |
| `X-OpenApi-Client-Id` | Client ID |
| `X-OpenApi-Timestamp` | Unix 秒时间戳 |
| `X-OpenApi-Nonce` | 16 到 128 个可打印 ASCII 字符的一次性随机数 |
| `X-OpenApi-Signature` | 对 canonical request 使用 HMAC-SHA256 后的无填充 Base64URL 值 |

canonical request 的字段顺序固定为：

```text
OPENAPI-HMAC-SHA256
<client id>
<uppercase HTTP method>
<request path>
<sorted canonical query>
<lowercase SHA-256 hex of body>
<Unix timestamp seconds>
<nonce>
```

服务端先验证时间窗、nonce 和签名，再建立当前请求的 APPLICATION Session。
同一 Account 下的 nonce 只能成功使用一次。
请求体会在 Filter 中缓存，保证签名校验和 Controller 参数绑定读取同一份内容。

## 扩展方式

开放一个新接口，或替换授权检查，都通过 Spring Bean 完成。

| 要接入的内容 | 应用实现 | 声明方式 | 框架发现与校验 |
|---|---|---|---|
| 开放接口 | 带 `@OpenApiRoute` 与 `@RequirePermission` 的 Controller 方法 | `@RestController` Bean | `OpenApiRouteCatalog` 在单例初始化后扫描，校验路径、Tool 标识、Operation 和权限注解，失败阻止启动 |
| Token 认证端点 | `OpenApiAuthenticationEndpoint` | Spring Bean | 自动配置用 `@ConditionalOnBean(OpenApiAuthenticationEndpoint.class)` 装配 Token 处理 |
| Operation | `OperationModule` 声明的 Operation 目录 | Spring Bean | `OperationCatalog` 收集全部模块，路由和 Client 按编码查询 |
| Client 授权检查 | 自定义 `OpenApiClientOperationChecker` | Spring Bean | 自动配置使用 `@ConditionalOnMissingBean`，应用 Bean 替换默认的持久化实现 |

增加一条新开放路由的步骤：

1. 在 `OperationModule` 中声明新的 Operation。
2. 在 Controller 方法上添加 `@OpenApiRoute` 和相同 Operation 的 `@RequirePermission`。
3. 启动应用，确认路由校验通过。
4. 调用 `replaceOperations`，把新的 Operation 授予需要的 Client。

接入后的验证：打开 Swagger UI，在文档选择器中选择「开放 API」，确认新路由出现。

## 运行时 Swagger 与验证

Springdoc 存在且文档端点启用时，Swagger UI 的文档选择器显示独立的「开放 API」分组。
这个分组只包含：

- 具体应用显式标记为 `@OpenApiRoute` 的业务方法；
- 具体应用通过 `OpenApiAuthenticationEndpoint` Bean 选择的认证端点。

普通管理接口不会混入这个分组。
模块自动添加 `openApiBearer`、`openApiHmac` 和认证端点使用的 `openApiClientBasic` security scheme，并给开放业务方法补充 Bearer 与 HMAC 二选一的安全要求。

| 地址 | 内容 |
| --- | --- |
| `/swagger-ui/index.html` | Swagger UI；在文档选择器中选择「开放 API」 |
| `/v3/api-docs/openapi` | Swagger UI 使用的「开放 API」分组运行时数据源 |

仓库不生成或提交静态 OpenAPI 文件。

验证步骤：

1. 创建 Client，保存一次性凭据。
2. 用 Token 或 HMAC 调用 `GET /openapi/workspaces/42/documents/9001`，确认返回 Document。
3. 用未授权的 Operation 的 Client 调用，确认被拒绝。
4. 停用 Client，再调用 Token 端点和业务路由，确认都被拒绝。
5. 使用同一 nonce 重复 HMAC 请求，确认第二次失败。

## 失败结果与限制

| 条件 | 结果 |
|---|---|
| `grant_type` 不是 `client_credentials`、Basic 格式错误、Client 不存在或已停用、Account 不可用，或 Secret 错误 | Token 端点返回统一认证失败 |
| 认证端点路径不是非根绝对路径，或包含结尾斜杠、重复斜杠、查询字符串、片段 | `IllegalArgumentException`，启动失败 |
| 路由 `path` 不以 `/openapi/` 开头，或包含重复斜杠、查询参数、片段 | `IllegalArgumentException` |
| `@OpenApiRoute` 声明了多个 HTTP 方法或多个路径 | `IllegalArgumentException` |
| 路由、Tool 名称或标准操作标识重复 | `IllegalStateException`，启动失败 |
| 方法缺少与路由相同 Operation 的 `@RequirePermission` | `IllegalStateException`，启动失败 |
| `create` 的 Operation 没有声明为开放路由 | `IllegalArgumentException`：Operation 尚未声明为 OpenAPI 路由 |
| `create` 的创建者没有该 Operation 的权限 | `IllegalArgumentException`：当前 Session 没有 Operation 权限 |
| `create` 的认证方法不是 `shared-secret` 或 `hmac` | `IllegalArgumentException` |
| 默认的「不可用」检查实现被调用 | 抛出 `IllegalStateException`：当前应用没有 OpenAPI Client Operation 检查实现 |
| HMAC Header 名称无效或重复，或路径前缀不合法 | `IllegalStateException`，启动失败 |

通用的 `400`、`401`、`403` 和 `500` 使用统一的 `OpenApiError` 结构。

- OpenAPI Client 只能开放已经声明为 `@OpenApiRoute` 的固定 Operation，不能通过请求参数临时扩大权限。
- Token 和 HMAC 认证都要求 Client 仍为 `ACTIVE`，且请求最终通过普通 `@RequirePermission` 权限检查。
- Client 使用 APPLICATION Subject 进入与普通请求相同的 Call 与权限链，开放接口不另建授权通道。
- HMAC 签名覆盖完整请求路径、排序后的查询字符串和请求体。客户端与服务端必须使用同一规范化规则。
- 开放路由仍属于具体应用 Controller。OpenAPI 模块不替具体应用决定资源范围、数据查询或业务请求与响应结构。

## 相关页面

- [接入身份与权限](/tutorials/identity-and-permission)
- [接入认证与会话](/tutorials/authentication-and-session)
- [部署 Gateway 与接入回调](/tutorials/gateway-and-callbacks)
- [OpenAPI Client](/capabilities/identity-access/openapi-client)
- [Operation 声明](/capabilities/identity-access/operation-declaration)
