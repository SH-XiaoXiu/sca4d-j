# 配置规则

## 单一来源

真实运行入口是类型化 Properties、Spring/Vite 配置和部署环境；`.env.example` 是完整清单。禁止文档、脚本和代码各维护不同默认值。

## 命名

项目配置统一 `sca4dj`/`SCA4DJ_` 前缀，框架原生配置沿用原名。禁止同一含义提供多个别名和长期兼容键。

## 默认值

开发默认值必须安全并写明用途。生产环境必须填写敏感配置、外部地址、可信代理和允许的 Origin。空值不得自动放宽权限、使用弱密钥或开放访问。

## 类型化绑定

新增配置使用 `Sca4djXxxProperties` 和对应 Config，禁止在业务类中散落 `@Value`。应用启动时校验枚举值和数值范围。

## 清单同步

新增、改名或删除配置必须同步 `.env.example`、YAML、Compose、运维说明和[配置参考](/reference/configuration.md)，并通过 `./gradlew checkEnvExample`。

## 敏感信息

凭据、签名密钥和 token 禁止进入 Git、日志、健康详情和前端构建。由部署平台注入并支持轮换。
