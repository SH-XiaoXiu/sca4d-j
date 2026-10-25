# 配置规则

本页规定配置项的来源、命名、默认值、类型化绑定、清单同步和敏感信息处理。本页适用于新增或修改配置的开发者与维护者。读完后，开发者能确定配置应写在哪里，以及新增配置需要同步哪些文件。

## 单一来源

真实运行入口是类型化 Properties、Spring/Vite 配置和部署环境。`.env.example` 是完整清单。禁止文档、脚本和代码各自维护不同的默认值。

类型化绑定的同类写法见[全局 ID](/capabilities/foundation/global-id)中的 `IdProperties`，默认值和范围检查都在绑定类中。

## 命名

脚手架配置统一使用 `sca4dj`/`SCA4DJ_` 前缀，框架原生配置沿用原名。禁止同一含义提供多个别名和长期兼容键。

## 默认值

开发默认值必须安全，并写明用途。

生产环境必须填写敏感配置、外部地址、可信代理和允许的 Origin。空值不得自动放宽权限、使用弱密钥或开放访问。

## 类型化绑定

新增配置使用 `XxxProperties` 类型化绑定，并在对应的自动配置中注册，例如 `IdProperties`、`RedisRateLimitProperties`。禁止在业务类中散落 `@Value`。应用启动时校验枚举值和数值范围。

## 清单同步

新增、改名或删除配置时，必须同步以下内容：

- `.env.example`
- YAML
- Compose
- 运维说明
- 具体应用的运行配置说明

同步后，运行 `./gradlew checkEnvExample`。

## 敏感信息

凭据、签名密钥和 token 禁止进入 Git、日志、健康详情和前端构建。凭据由部署平台注入，并支持轮换。

## 示例

以下示例为「工作区」模块增加一个配置项。`WorkspaceProperties` 与 `WorkspaceAutoConfiguration` 由应用定义，写法参照 `IdProperties`。

```java
@ConfigurationProperties("sca4dj.workspace")
public class WorkspaceProperties {

    /** 单个工作区允许的成员上限，默认 200。 */
    private int maxMembers = 200;

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        if (maxMembers < 1) {
            throw new IllegalArgumentException("成员上限必须大于零");
        }
        this.maxMembers = maxMembers;
    }
}
```

```yaml
sca4dj:
  workspace:
    max-members: 200
```

```bash
./gradlew checkEnvExample
```

绑定类在 setter 中校验范围，应用启动时即暴露错误。业务类通过注入 `WorkspaceProperties` 读取值，不使用 `@Value`。

新增配置后，同步 `.env.example`、YAML、Compose 和运维说明，再运行 `checkEnvExample`。
