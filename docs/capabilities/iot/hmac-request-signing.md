# HmacCanonicalRequest

源码：具体应用的 Server 模块中，路径由应用决定，例如 `apps/<应用名>-server/src/main/java/<应用包路径>/iot/`

HmacCanonicalRequest 定义具体应用的 Device HTTP HMAC 签名输入。它属于具体应用，因为 HTTP 路由、请求头和 query 规范化规则不是通用 Auth 或 IoT 模块的职责。

调用方必须传入以下参数：

- 正数 deviceId；
- 可规范化为大写的 HTTP method；
- 以 `/` 开始且不含换行的 path；
- 已按名称和值排序并使用 RFC 3986 百分号编码的 query；
- 完整 body；
- 秒级时间；
- 16 到 128 个可打印 ASCII 字符的 nonce。

`canonicalBytes()` 返回以下 UTF-8 内容，其中 body 使用 SHA-256 小写十六进制摘要：

```text
IOT-HMAC-SHA256
{deviceId}
{METHOD}
{path}
{canonicalQuery}
{bodySha256}
{epochSecond}
{nonce}
```

HmacAuthenticationMethod 只验证 `canonicalBytes()` 返回的字节和签名，不知道 HTTP 字段如何产生。HmacAuthenticationMethod 的配置与凭据保存方式见 [HMAC 认证](/capabilities/authentication-session/hmac)。

## 示例

以下示例对应温控器（Thermostat）上报遥测的 HTTP 请求。请求体为 JSON，签名时间为 `1793000000`，nonce 为 `b1f0c2d3e4a5f607`。

签名输入的 UTF-8 内容如下，`{bodySha256}` 由请求体计算得出：

```text
IOT-HMAC-SHA256
1001
POST
/api/devices/1001/telemetry
limit=10
<请求体的 SHA-256 小写十六进制摘要>
1793000000
b1f0c2d3e4a5f607
```

计算请求体摘要时使用 JDK 提供的 `MessageDigest` 与 `HexFormat`：

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class ThermostatRequestDigest {

    public static String bodySha256(byte[] body) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(body));
    }

    public static byte[] canonicalBytes(String bodySha256) {
        String text = String.join("\n",
                "IOT-HMAC-SHA256",
                "1001",
                "POST",
                "/api/devices/1001/telemetry",
                "limit=10",
                bodySha256,
                "1793000000",
                "b1f0c2d3e4a5f607");
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
```

`bodySha256(...)` 的结果即 `{bodySha256}` 字段。签名时对 `canonicalBytes(...)` 返回的字节计算 HMAC，签名值由设备保存的 Secret 生成。示例只展示输入拼接，不展示 Secret 处理。
## 扩展方式

具体应用实现自己的 canonical 输入。它按本页规则构造 `canonicalBytes()` 的内容，再交给 HMAC 认证方法验证签名。Auth 模块只读取字节和签名，不读取 HTTP 请求对象。
