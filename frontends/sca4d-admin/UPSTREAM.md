# Vue Vben Admin 来源

`internal/`、`packages/`、`scripts/vsh/` 和 `scripts/turbo-run/` 来自开源项目 [Vue Vben Admin](https://github.com/vbenjs/vue-vben-admin)：

- 版本：`v5.7.0`
- Commit：`63a38dce49ba109f61607994e21ba921d8e970e9`
- License：MIT，完整文本见 `LICENSE`
- 原作者：vben（ann.vben@gmail.com）
- 项目主页：https://github.com/vbenjs/vue-vben-admin

`examples/vue-vben-admin/` 原样保留该版本完整的 `apps/web-antd` 示例源码，不加入当前 pnpm workspace，也不注册到运行时路由。`internal/`、`packages/`、`scripts/vsh/` 和 `scripts/turbo-run/` 与该 Tag 保持一致。

`apps/sca4d-admin/` 是 Sca4d Server Admin 配套示例。它沿用 Vben 的 `Page`、`Profile`、`VxeGrid`、表单和交互组件，只接入 Sca4d Server 的账号、角色、permission、个人资料和安全设置功能。认证方式、认证策略、修改密码和动作认证窗口使用应用内 feature 组件组合，不修改 Vben 上游组件。它不移植旧前端页面，也不维护另一套布局、主题或内部导航。上游分析页、工作台、组件演示和静态数据只保留在 `examples/vue-vben-admin/` 中。

采用 Vue Vben Admin 是因为脚手架不应长期维护通用后台布局、主题和组件页面。具体应用一旦需要自己的交互和视觉设计，通常会重写前端；Sca4d-J 因此复用成熟开源界面作为默认示例，只维护 Sca4d Server 的手写契约、字段映射和业务动作接入代码。

升级时从新的明确 Tag 重新导入上游目录，再重新执行类型检查、单元测试、浏览器验收和构建。不要在日常业务修改中直接改写 `internal/` 或 `packages/`；具体应用需要不同页面时，可以替换整个示例应用。
