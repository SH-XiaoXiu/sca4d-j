import type { DuxThemeConfig } from '@duxweb/vitepress-theme'
import { withDuxTheme } from '@duxweb/vitepress-theme/config'

const handbook = [
  { text: '开始使用', items: [
    { text: '功能概览', link: '/handbook/' },
    { text: '适用场景', link: '/handbook/project-positioning' },
    { text: '选型与扩展参考', link: '/handbook/evaluation-criteria' },
    { text: '第一次运行', link: '/handbook/getting-started' },
    { text: '工程结构', link: '/handbook/project-structure' },
    { text: '配置本地环境', link: '/handbook/configuration' },
    { text: '开发命令', link: '/handbook/daily-development' },
    { text: '构建与发布', link: '/handbook/build-and-release' },
    { text: '创建业务项目', link: '/handbook/template-adoption' },
    { text: '维护项目文档', link: '/handbook/documentation' }
  ]}
]

const authorNote = [
  { text: '作者的话', items: [
    { text: '项目起源与设计思想', link: '/author-note/' }
  ]}
]

const architecture = [
  { text: '系统设计', items: [
    { text: '总体架构', link: '/architecture/' },
    { text: '应用、网关和外部服务', link: '/architecture/application-boundaries' },
    { text: '应用分层与请求链', link: '/architecture/layers-and-request-flow' },
    { text: 'IAM 身份与访问模型', link: '/architecture/iam' },
    { text: '认证过程与 MFA', link: '/architecture/authentication-and-mfa' },
    { text: 'Session、Principal 选择与请求身份', link: '/architecture/identity-and-session' },
    { text: 'CurrentRequest', link: '/architecture/current-request' },
    { text: '权限与资源访问', link: '/architecture/permission-and-access' },
    { text: '数据、事务与一致性', link: '/architecture/data-and-consistency' },
    { text: '多实例运行模型', link: '/architecture/runtime-topology' }
  ]}
]

const development = [
  { text: '功能开发', items: [
    { text: '开发指南首页', link: '/development/' },
    { text: '从需求到交付', link: '/development/feature-workflow' },
    { text: 'Web API', link: '/development/web-api' },
    { text: '数据访问', link: '/development/data-access' },
    { text: '权限与数据范围', link: '/development/permission' },
    { text: '并发、重复执行与审计', link: '/development/concurrency-and-audit' },
    { text: '消息、实时更新与通知', link: '/development/messaging-and-realtime' },
    { text: '接入业务文件', link: '/development/file-integration' },
    { text: '管理端功能开发', link: '/development/frontend' }
  ]},
  { text: '公告模块完整示例', collapsed: false, items: [
    { text: '示例首页', link: '/development/example/announcement/' },
    { text: '数据模型与迁移', link: '/development/announcement-model' },
    { text: 'Mapper 与 Repository', link: '/development/announcement-persistence' },
    { text: 'Service 业务流程', link: '/development/announcement-service' },
    { text: 'Request、View 与 Controller', link: '/development/announcement-web' },
    { text: '权限、访问范围与管理端', link: '/development/announcement-integration' },
    { text: '测试与验收', link: '/development/announcement-verification' }
  ]}
]

const aiCollaboration = [
  { text: 'AI 协作', items: [
    { text: 'AI 协作设计', link: '/ai-collaboration/' },
    { text: '执行规则', link: '/ai-collaboration/execution-rules' },
    { text: '交付可维护代码', link: '/ai-collaboration/tutorial' },
    { text: 'Goal 任务提示词模板', link: '/ai-collaboration/prompt-template' }
  ]}
]

const standards = [
  { text: '项目规范', items: [{ text: '规范首页', link: '/standards/' }] },
  { text: '工程哲学', items: [
    { text: '核心编码规则', link: '/standards/general/core-rules' },
    { text: '可预测的工程结构', link: '/standards/philosophy/predictable-architecture' },
    { text: '工程验收原则', link: '/standards/philosophy/acceptance-principles' }
  ]},
  { text: '基础规则', items: [
    { text: '代码结构', link: '/standards/general/code-structure' },
    { text: '验收规则', link: '/standards/general/acceptance' }
  ]},
  { text: '通用编码规范', collapsed: true, items: [
    { text: '命名、类型与显式契约', link: '/standards/general/naming-and-types' },
    { text: '抽象与流程', link: '/standards/general/abstraction-and-flow' },
    { text: '分层与 API', link: '/standards/general/layers-and-api' },
    { text: '注释与成品文档', link: '/standards/general/comments-and-docs' },
    { text: '测试、评审与验收', link: '/standards/general/testing-and-acceptance' },
    { text: '代码评审覆盖规范', link: '/standards/general/review-execution' },
    { text: '变更范围', link: '/standards/general/change-scope' }
  ]},
  { text: '技术栈规范', collapsed: true, items: [
    { text: 'Java 与 Spring', link: '/standards/stack/java-and-spring' },
    { text: 'Java 工程完整审查', link: '/standards/stack/java-engineering-review' },
    { text: 'MyBatis-Flex 与持久化', link: '/standards/stack/persistence' },
    { text: '持久化分层完整规范', link: '/standards/stack/persistence-boundary' },
    { text: 'PostgreSQL 与数据库', link: '/standards/stack/database' },
    { text: 'Vue、TypeScript 与交互', link: '/standards/stack/frontend' },
    { text: '前端质量完整规范', link: '/standards/stack/frontend-quality-review' },
    { text: 'Sca4d-J 项目工程约定', link: '/standards/stack/sca4d-project-conventions' },
    { text: '格式化与构建', link: '/standards/stack/formatting-and-build' }
  ]},
  { text: '内置功能规则', collapsed: true, items: [
    { text: '认证', link: '/standards/facilities/authentication' },
    { text: '权限', link: '/standards/facilities/permission' },
    { text: 'CurrentRequest', link: '/standards/facilities/request-context' },
    { text: '并发', link: '/standards/facilities/concurrency' },
    { text: '消息', link: '/standards/facilities/messaging' },
    { text: '实时通信', link: '/standards/facilities/realtime' },
    { text: '文件', link: '/standards/facilities/file' },
    { text: '审计', link: '/standards/facilities/audit' },
    { text: '应用日志', link: '/standards/facilities/logging' },
    { text: '缓存', link: '/standards/facilities/cache' },
    { text: '配置', link: '/standards/facilities/configuration' },
    { text: '管理端', link: '/standards/facilities/admin-ui' }
  ]}
]

const infrastructure = [
  { text: '内置功能', items: [
    { text: '功能列表', link: '/infrastructure/' },
    { text: '缓存', link: '/infrastructure/cache' },
    { text: '消息发布与 Outbox', link: '/infrastructure/messaging' },
    { text: '实时通信', link: '/infrastructure/realtime' },
    { text: '文件服务', link: '/infrastructure/file' },
    { text: '邮件发送', link: '/infrastructure/mail' },
    { text: '审计与操作日志', link: '/infrastructure/audit' },
    { text: '通知', link: '/infrastructure/notification' },
    { text: '即时通信', link: '/infrastructure/im' },
    { text: '并发基础设施', link: '/infrastructure/concurrency' },
    { text: 'Quartz 集群调度', link: '/infrastructure/scheduler' },
    { text: '系统字典', link: '/infrastructure/dictionaries' },
    { text: '可选网关', link: '/infrastructure/gateway' },
    { text: '管理端基础', link: '/infrastructure/admin' }
  ]}
]

const operations = [
  { text: '配置、部署与运维', items: [
    { text: '运维首页', link: '/operations/' },
    { text: '环境划分', link: '/operations/environments' },
    { text: '配置组织与覆盖', link: '/operations/configuration' },
    { text: '本地依赖', link: '/operations/local-dependencies' },
    { text: '构建、版本与产物', link: '/operations/build-and-package' },
    { text: '部署', link: '/operations/deployment' },
    { text: '启动、健康与观测', link: '/operations/startup-and-health' },
    { text: '日志与 Trace ID', link: '/operations/logging-and-tracing' },
    { text: '故障排查', link: '/operations/troubleshooting' }
  ]}
]

const reference = [
  { text: '参考手册', items: [
    { text: '参考首页', link: '/reference/' },
    { text: '配置参考', link: '/reference/configuration' },
    { text: 'HTTP 接口', link: '/reference/http' },
    { text: '接口与注解', link: '/reference/interfaces-annotations' },
    { text: '枚举与状态', link: '/reference/enums-and-status' },
    { text: '模块目录', link: '/reference/module-catalog' },
    { text: '第三方依赖目录', link: '/reference/third-party-dependencies' },
    { text: '错误码与术语', link: '/reference/glossary' }
  ]},
  { text: '公共 Java API', collapsed: true, items: [
    { text: 'Web 与通用类型', link: '/reference/api/web-core' },
    { text: '请求与认证', link: '/reference/api/request-auth' },
    { text: '权限与数据范围', link: '/reference/api/permission-access' },
    { text: '并发与审计', link: '/reference/api/concurrency-audit' },
    { text: '消息与实时通信', link: '/reference/api/messaging-realtime' },
    { text: '文件、缓存与启动检查', link: '/reference/api/file-cache-boot' },
    { text: 'Spring 装配类型', link: '/reference/api/configuration-components' },
    { text: '业务服务', link: '/reference/api/business-services' },
    { text: '调度', link: '/reference/api/scheduler' },
    { text: 'Gateway 与管理端', link: '/reference/api/gateway-admin' }
  ]}
]

const documentation = [
  { text: '文档维护', items: [
    { text: '维护说明', link: '/documentation/' },
    { text: '文档结构', link: '/documentation/structure' },
    { text: '变化与维护位置', link: '/documentation/change-routing' },
    { text: '记录业务功能', link: '/documentation/feature-changes' },
    { text: '记录内置功能', link: '/documentation/infrastructure-changes' },
    { text: '新增、修改与退役规则', link: '/documentation/lifecycle' },
    { text: '审查与完成标准', link: '/documentation/review' }
  ]}
]

const themeConfig = {
  logo: '/logo.png',
  siteTitle: 'Sca4d-J',
  nav: [
    { text: '开始使用', link: '/handbook/' },
    { text: '作者的话', link: '/author-note/' },
    { text: '作者博客', link: 'https://www.xiuxius.cn/' },
    { text: '系统设计', link: '/architecture/' },
    { text: '开发指南', link: '/development/' },
    { text: 'AI 协作', link: '/ai-collaboration/' },
    { text: '项目规范', link: '/standards/' },
    { text: '内置功能', link: '/infrastructure/' },
    { text: '运维与参考', items: [
      { text: '配置、部署与运维', link: '/operations/' },
      { text: '参考手册', link: '/reference/' },
      { text: '文档维护', link: '/documentation/' }
    ]}
  ],
  socialLinks: [
    {
      icon: 'github',
      link: 'http://github.com/sH-XiaoXiu/sca4d-j',
      ariaLabel: 'Sca4d-J GitHub'
    }
  ],
  sidebar: {
    '/handbook/': handbook,
    '/author-note/': authorNote,
    '/architecture/': architecture,
    '/development/': development,
    '/ai-collaboration/': aiCollaboration,
    '/standards/': standards,
    '/infrastructure/': infrastructure,
    '/operations/': operations,
    '/reference/': reference,
    '/documentation/': documentation
  },
  outline: { label: '本页内容', level: [2, 3] },
  lastUpdated: {
    formatOptions: { dateStyle: 'medium', timeStyle: 'short' }
  },
  langs: {
    search: {
      button: {
        buttonText: '搜索文档',
        buttonAriaLabel: '搜索文档'
      },
      modal: {
        noResultsText: '没有找到相关内容',
        resetButtonTitle: '清除搜索条件',
        backButtonTitle: '关闭搜索',
        footer: {
          selectText: '选择',
          navigateText: '切换',
          closeText: '关闭'
        }
      }
    },
    sidebar: {
      toggleButtonText: '目录',
      mobileTitle: '文档目录',
      docNavTitle: '文档导航'
    },
    docFooter: {
      lastUpdatedText: '最后更新',
      prevPageText: '上一篇',
      nextPageText: '下一篇'
    }
  }
} satisfies DuxThemeConfig

export default withDuxTheme({
  lang: 'zh-CN',
  title: 'Sca4d-J',
  description: 'Scaffold for Java - 通用 Java 后端工程模板与项目手册',
  cleanUrls: true,
  lastUpdated: true,
  head: [['meta', { name: 'theme-color', content: '#15705f' }]],
  themeConfig
})
