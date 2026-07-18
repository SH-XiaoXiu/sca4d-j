# 第三方依赖目录

项目使用的第三方依赖库按后端、管理端、测试、构建和文档站分类，并列出用途、官方网站、源代码仓库和开源协议。

## 后端与网关

| 依赖库                       | 项目用途                                             | 官方资料与开源协议                                                                                                                                                                      |
|---------------------------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sa-Token                  | 保存登录 Session，并检查当前 Principal 的功能权限               | [官方网站](https://sa-token.cc/) · [源代码](https://github.com/dromara/Sa-Token) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                                            |
| Hutool                    | 生成 ID、计算请求摘要，并校验和转换文本                            | [官方网站](https://www.hutool.cn/) · [源代码](https://github.com/dromara/hutool) · [MulanPSL-2.0](https://license.coscl.org.cn/MulanPSL2)                                             |
| Bucket4j                  | 在 Redis 中保存令牌桶状态并限制请求频率                          | [官方网站](https://bucket4j.com/) · [源代码](https://github.com/bucket4j/bucket4j) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                                          |
| Caffeine                  | 提供主应用本机缓存，并缓存已编译的邮件模板                            | [源代码](https://github.com/ben-manes/caffeine) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                                                                         |
| Mustache.java             | 将邮件模板和变量渲染为邮件正文                                  | [源代码](https://github.com/spullara/mustache.java) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                                                                     |
| Cedar Java                | 解析 Cedar Policy 并判断当前请求是否可以访问资源                  | [官方网站](https://www.cedarpolicy.com/) · [源代码](https://github.com/cedar-policy/cedar-java) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                             |
| Flowable                  | 保存认证实例、当前任务、认证方式引用和过期时间，并处理多因素认证的分支              | [官方网站](https://www.flowable.com/open-source) · [源代码](https://github.com/flowable/flowable-engine) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                    |
| OWASP Java HTML Sanitizer | 删除邮件正文中不允许的 HTML 标签和属性                           | [项目主页](https://owasp.org/www-project-java-html-sanitizer/) · [源代码](https://github.com/OWASP/java-html-sanitizer) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)     |
| Bouncy Castle Provider    | 提供密码和验证码 Argon2id 摘要所需的算法实现                      | [官方网站](https://www.bouncycastle.org/java.html) · [MIT](https://www.bouncycastle.org/about/bouncy-castle-licence/)                                                              |
| springdoc-openapi         | 根据 Controller 和 Java 类型生成 OpenAPI 文档与 Swagger UI | [官方网站](https://springdoc.org/) · [源代码](https://github.com/springdoc/springdoc-openapi) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                               |
| MinIO Java SDK            | 写入、读取和删除文件对象，并生成预签名访问地址                          | [官方网站](https://min.io/docs/minio/linux/developers/java/minio-java.html) · [源代码](https://github.com/minio/minio-java) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) |
| MyBatis                   | 执行 Mapper XML 中的 SQL 并转换查询结果                     | [官方网站](https://mybatis.org/mybatis-3/) · [源代码](https://github.com/mybatis/mybatis-3) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                                 |
| MyBatis-Flex              | 映射数据库 Entity、构建查询条件并执行分页查询                       | [官方网站](https://mybatis-flex.com/) · [源代码](https://github.com/mybatis-flex/mybatis-flex) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                              |
| Redisson                  | 执行分布式锁，并为 Bucket4j 读写 Redis 令牌桶状态                | [官方网站](https://redisson.pro/) · [源代码](https://github.com/redisson/redisson) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)                                          |
| Flyway Community          | 应用启动时按版本执行 PostgreSQL 迁移脚本                       | [官方网站](https://documentation.red-gate.com/flyway) · [源代码](https://github.com/flyway/flyway) · [Apache-2.0](https://flywaydb.org/licenses/flyway-oss)                           |
| PostgreSQL JDBC Driver    | 建立 PostgreSQL JDBC 连接并识别数据库约束错误                  | [官方网站](https://jdbc.postgresql.org/) · [源代码](https://github.com/pgjdbc/pgjdbc) · [BSD-2-Clause](https://jdbc.postgresql.org/about/license.html)                                |

## 管理端

| 依赖库                  | 项目用途                            | 官方资料与开源协议                                                                                                                                                                      |
|----------------------|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Axios                | 发送管理端 HTTP 请求并上传文件              | [官方网站](https://axios-http.com/) · [源代码](https://github.com/axios/axios) · [MIT](https://spdx.org/licenses/MIT.html)                                                            |
| Day.js               | 解析和格式化接口时间，并生成日期筛选值             | [官方网站](https://day.js.org/) · [源代码](https://github.com/iamkun/dayjs) · [MIT](https://spdx.org/licenses/MIT.html)                                                               |
| EventSource Client   | 建立 SSE 连接并接收服务端实时消息             | [源代码](https://github.com/rexxars/eventsource-client) · [MIT](https://spdx.org/licenses/MIT.html)                                                                               |
| Quartz Cron Parser   | 提交 Schedule 前校验 Quartz Cron 表达式 | [源代码](https://github.com/JoshOY/quartz-cron-parser) · [MIT](https://spdx.org/licenses/MIT.html)                                                                                |
| Ant Design Vue       | 渲染管理端表单、表格、对话框和操作反馈             | [官方网站](https://www.antdv.com/) · [源代码](https://github.com/vueComponent/ant-design-vue) · [MIT](https://spdx.org/licenses/MIT.html)                                             |
| Lucide               | 渲染菜单、按钮和状态提示图标                  | [官方网站](https://lucide.dev/) · [源代码](https://github.com/lucide-icons/lucide) · [ISC](https://spdx.org/licenses/ISC.html)                                                        |
| SortableJS           | 调整菜单和字典项顺序                      | [官方网站](https://sortablejs.github.io/Sortable/) · [源代码](https://github.com/SortableJS/Sortable) · [MIT](https://spdx.org/licenses/MIT.html)                                     |
| Vue Advanced Cropper | 裁剪、缩放和旋转 DEFAULT Profile 头像       | [官方网站](https://advanced-cropper.github.io/vue-advanced-cropper/) · [源代码](https://github.com/advanced-cropper/vue-advanced-cropper) · [MIT](https://spdx.org/licenses/MIT.html) |

## 测试与构建

| 依赖库                | 项目用途                               | 官方资料与开源协议                                                                                                                                            |
|--------------------|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spotless           | 按项目格式配置检查 Java 和工程文本               | [官方网站](https://diffplug.github.io/spotless/) · [源代码](https://github.com/diffplug/spotless) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) |
| JUnit 5            | 执行后端和网关单元测试                        | [官方网站](https://junit.org/junit5/) · [源代码](https://github.com/junit-team/junit5) · [EPL-2.0](https://spdx.org/licenses/EPL-2.0.html)                  |
| openapi-typescript | 根据后端 OpenAPI 文档生成管理端 TypeScript 类型 | [官方网站](https://openapi-ts.dev/) · [源代码](https://github.com/openapi-ts/openapi-typescript) · [MIT](https://spdx.org/licenses/MIT.html)                |
| Vitest             | 执行管理端 common 和 app 单元测试            | [官方网站](https://vitest.dev/) · [源代码](https://github.com/vitest-dev/vitest) · [MIT](https://spdx.org/licenses/MIT.html)                                |
| Playwright         | 在浏览器中验证登录和管理页面操作                   | [官方网站](https://playwright.dev/) · [源代码](https://github.com/microsoft/playwright) · [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)           |
| Vue Test Utils     | 在单元测试中挂载 Vue 组件并触发交互               | [官方网站](https://test-utils.vuejs.org/) · [源代码](https://github.com/vuejs/test-utils) · [MIT](https://spdx.org/licenses/MIT.html)                       |

## 文档站

| 依赖库                 | 项目用途                  | 官方资料与开源协议                                                                                                              |
|---------------------|-----------------------|------------------------------------------------------------------------------------------------------------------------|
| VitePress           | 将 Markdown 页面构建为项目文档站 | [官方网站](https://vitepress.dev/) · [源代码](https://github.com/vuejs/vitepress) · [MIT](https://spdx.org/licenses/MIT.html) |
| Dux VitePress Theme | 渲染文档导航、搜索、正文和明暗主题     | [源代码](https://github.com/duxweb/vitepress-theme) · [MIT](https://spdx.org/licenses/MIT.html)                           |
