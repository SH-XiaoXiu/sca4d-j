export interface PlatformModule {
  id: string
  title: string
  description: string
  details: string[]
  icon: string
  link: string
}

export interface PlatformModuleGroup {
  id: string
  title: string
  description: string
  modules: PlatformModule[]
}

export interface HeroBranch {
  id: string
  title: string
  caption: string
}

export interface TechnologyGroup {
  title: string
  items: string[]
}

export interface RoadmapItem {
  title: string
  description: string
  details: string[]
  icon: string
}

export interface RoadmapGroup {
  title: string
  description: string
  items: RoadmapItem[]
}

export const heroBranches: HeroBranch[] = [
  { id: 'authentication', title: 'IAM 与访问', caption: '账号、认证、身份源与权限' },
  { id: 'organization', title: '多租户协作', caption: '用户、租户、成员与工作空间' },
  { id: 'concurrency', title: '可靠运行', caption: '缓存、幂等、锁、限流与调度' },
  { id: 'messaging', title: '消息与实时', caption: '异步消息、SSE 与 WebSocket' },
  { id: 'file-mail', title: '文件与邮件', caption: '文件存储、签名访问与邮件发送' },
  { id: 'communication', title: '通知与会话', caption: '通知、即时通信与已读状态' },
  { id: 'runtime', title: '多实例部署', caption: '共享状态、健康检查与故障恢复' },
  { id: 'admin-gateway', title: '管理端与网关', caption: '管理应用、API 契约与入口路由' }
]

export const platformModuleGroups: PlatformModuleGroup[] = [
  {
    id: 'identity',
    title: '身份与访问',
    description: '账号、认证方式、身份源和资源访问规则。',
    modules: [
      {
        id: 'iam',
        title: 'IAM',
        description: '使用同一套身份结构管理全局账号、租户账号、登录会话和可进入的工作空间。',
        details: ['全局与租户账号', '会话与登录设备', '工作空间选择'],
        icon: 'badge-check',
        link: '/architecture/iam'
      },
      {
        id: 'mfa',
        title: 'MFA 多因素认证',
        description: '在登录和敏感操作前增加第二项验证，并在验证设备不可用时使用恢复码。',
        details: ['TOTP 身份验证器', '邮件验证码', '一次性恢复码'],
        icon: 'shield-check',
        link: '/architecture/authentication-and-mfa'
      },
      {
        id: 'passkey',
        title: 'Passkey 无密码认证',
        description: '使用设备生物识别、PIN 或安全密钥完成 WebAuthn 登录和二次验证。',
        details: ['无密码登录', '多个 Passkey', '签名与重放校验'],
        icon: 'fingerprint',
        link: '/architecture/authentication-and-mfa#当前认证方式'
      },
      {
        id: 'identity-provider',
        title: '企业身份源',
        description: '接入企业现有账号系统，完成外部登录、账号关联和即时注册。',
        details: ['OpenID Connect 与 OAuth 2.0', 'LDAP', 'JIT 注册与账号关联'],
        icon: 'network',
        link: '/architecture/iam#identityprovider-与-externalidentity'
      },
      {
        id: 'user-tenant',
        title: '用户与租户',
        description: '让平台运营人员和租户成员在各自工作空间中使用同一套用户主档。',
        details: ['平台用户', '租户与成员', '独立工作空间'],
        icon: 'building-2',
        link: '/reference/module-catalog'
      },
      {
        id: 'role-permission',
        title: '角色与权限',
        description: '分别配置平台和租户角色，并将功能权限与具体资源访问规则组合使用。',
        details: ['平台与租户角色', '功能权限目录', '资源访问规则'],
        icon: 'key-round',
        link: '/architecture/permission-and-access'
      }
    ]
  },
  {
    id: 'runtime',
    title: '应用运行',
    description: '缓存、并发保护、集群调度和入口路由。',
    modules: [
      {
        id: 'cache',
        title: '缓存',
        description: '在本地和共享缓存之间选择合适的数据读取方式，并同步多实例缓存变化。',
        details: ['Redis 共享缓存', 'Caffeine 本地缓存', '多实例缓存刷新'],
        icon: 'database',
        link: '/infrastructure/cache'
      },
      {
        id: 'concurrency',
        title: '并发控制',
        description: '保护重复请求和竞争写入，避免同一业务操作被并发执行多次。',
        details: ['乐观锁与版本冲突', '幂等请求', '分布式锁与限流'],
        icon: 'gauge',
        link: '/infrastructure/concurrency'
      },
      {
        id: 'scheduler',
        title: 'Quartz 调度',
        description: '在多个应用实例之间协调定时任务，并保留每次执行结果和故障恢复记录。',
        details: ['JDBC 集群调度', '执行记录', '节点故障恢复'],
        icon: 'calendar-clock',
        link: '/infrastructure/scheduler'
      },
      {
        id: 'gateway',
        title: '可选网关',
        description: '在独立入口转发请求，并只把流量发送到当前健康的应用实例。',
        details: ['统一入口路由', '主动健康检查', 'upstream 热更新'],
        icon: 'route',
        link: '/infrastructure/gateway'
      }
    ]
  },
  {
    id: 'communication',
    title: '内容与通信',
    description: '文件、异步消息、在线事件、邮件、通知和会话。',
    modules: [
      {
        id: 'file',
        title: '文件服务',
        description: '使用统一文件引用完成上传、受控读取和存储迁移，不让业务依赖具体存储位置。',
        details: ['本地存储与 MinIO', '上传授权', '读取签名与文件状态'],
        icon: 'files',
        link: '/infrastructure/file'
      },
      {
        id: 'messaging',
        title: '消息发布',
        description: '在事务提交后可靠发布业务消息，并对暂时失败的投递进行重试。',
        details: ['事务提交后发送', 'RabbitMQ 路由', 'Outbox 与失败重试'],
        icon: 'send',
        link: '/infrastructure/messaging'
      },
      {
        id: 'realtime',
        title: '实时通信',
        description: '把业务事件发送给当前节点或集群中在线的浏览器和客户端。',
        details: ['SSE', 'WebSocket 与 STOMP', '跨节点投递'],
        icon: 'radio',
        link: '/infrastructure/realtime'
      },
      {
        id: 'mail',
        title: '邮件发送',
        description: '集中维护 SMTP 发件配置和邮件模板，并在启用前完成预览与连接测试。',
        details: ['多项 SMTP 配置', '模板预览', '连接测试与发送'],
        icon: 'mail',
        link: '/infrastructure/mail'
      },
      {
        id: 'notification',
        title: '通知',
        description: '保存每位接收人的通知和已读状态，并实时刷新在线用户的未读数量。',
        details: ['通知列表', '未读数量', '已读与实时刷新'],
        icon: 'bell',
        link: '/infrastructure/notification'
      },
      {
        id: 'im',
        title: '即时通信',
        description: '在单聊和群聊中发送文本与文件消息，并同步成员的已读位置。',
        details: ['单聊与群聊', '文本与文件消息', '已读位置与撤回'],
        icon: 'messages-square',
        link: '/infrastructure/im'
      }
    ]
  },
  {
    id: 'management',
    title: '管理与工程',
    description: '管理应用、字典、菜单和操作记录。',
    modules: [
      {
        id: 'system',
        title: '字典、菜单与操作日志',
        description: '维护可查询的系统字典和工作空间菜单，并记录需要追溯的业务操作。',
        details: ['系统与业务字典', '工作空间菜单', '审计与操作日志'],
        icon: 'library',
        link: '/infrastructure/dictionaries'
      },
      {
        id: 'admin',
        title: '管理端',
        description: '使用同一套 API 契约构建平台、租户和个人工作空间的管理界面。',
        details: ['TypeScript API', 'Vue 与 Pinia', '国际化与浏览器测试'],
        icon: 'panels-top-left',
        link: '/infrastructure/admin'
      }
    ]
  }
]

export const technologyGroups: TechnologyGroup[] = [
  {
    title: '后端',
    items: ['Java', 'Spring Boot', 'Spring MVC', 'Spring AOP', 'Jakarta Validation', 'Spring Actuator', 'Gradle', 'Hutool']
  },
  {
    title: '数据',
    items: ['PostgreSQL', 'MyBatis', 'MyBatis-Flex', 'Flyway', 'Redis', 'Caffeine']
  },
  {
    title: '身份与安全',
    items: ['Sa-Token', 'Cedar Java', 'Spring Security Crypto', 'Bouncy Castle', 'Argon2id', 'AES-256-GCM']
  },
  {
    title: '基础服务',
    items: ['Redisson', 'Bucket4j', 'RabbitMQ', 'Quartz', 'MinIO', 'Spring Mail', 'Mailpit', 'Mustache.java', 'OWASP HTML Sanitizer']
  },
  {
    title: '接口与网关',
    items: ['Spring Cloud Gateway', 'Spring Cloud LoadBalancer', 'Reactor Netty', 'SSE', 'WebSocket', 'STOMP', 'springdoc-openapi', 'OpenAPI']
  },
  {
    title: '管理端',
    items: ['TypeScript', 'Vue', 'Pinia', 'Vue Router', 'Ant Design Vue', 'Axios', 'Vite', 'vue-i18n', 'Lucide', 'Day.js']
  },
  {
    title: '测试与文档',
    items: ['JUnit', 'Spring Boot Test', 'Vitest', 'Playwright', 'Vue Test Utils', 'ESLint', 'Spotless', 'VitePress', 'pnpm', 'Docker Compose']
  }
]

export const technologyRows = [
  technologyGroups.slice(0, 4).flatMap((group) => group.items),
  technologyGroups.slice(4).flatMap((group) => group.items)
]

export const roadmapGroups: RoadmapGroup[] = [
  {
    title: '开放 API 与账号委派',
    description: '计划支持第三方应用调用和主账号下的细粒度委派。',
    items: [
      {
        title: '开放 API',
        description: '将为第三方应用签发凭证，限制可调用资源并记录每次请求。',
        details: ['应用凭证', '授权与限流', '调用记录'],
        icon: 'braces'
      },
      {
        title: '子账户与 RAM',
        description: '将在主账号下创建子账户，并分别授予可使用的功能和资源。',
        details: ['子账户', '权限委派', '资源授权'],
        icon: 'user-cog'
      },
      {
        title: '层级结构',
        description: '将保存组织或资源的树形关系，并支持继承、移动和路径查询。',
        details: ['树形层级', '继承关系', '路径查询'],
        icon: 'git-fork'
      }
    ]
  },
  {
    title: '数据与流程',
    description: '计划支持可复用的数据处理任务和业务流转定义。',
    items: [
      {
        title: '数据处理 Pipeline',
        description: '将按既定顺序完成数据接入、转换、校验、重试和交付。',
        details: ['数据接入', '转换与校验', '重试与交付'],
        icon: 'list-filter'
      },
      {
        title: '工作流',
        description: '将根据节点、参与人和条件推进任务，并保留每次流转结果。',
        details: ['任务与节点', '参与人', '流转条件'],
        icon: 'workflow'
      },
      {
        title: '状态机',
        description: '将限制对象允许发生的状态变化，并在迁移后发布对应事件。',
        details: ['状态定义', '迁移规则', '守卫与事件'],
        icon: 'git-compare-arrows'
      }
    ]
  },
  {
    title: '专项模块',
    description: '计划支持支付、小程序和物联网项目常用的接入功能。',
    items: [
      {
        title: '支付',
        description: '将记录支付单并处理渠道回调、退款、对账和重复请求。',
        details: ['渠道接入', '支付与退款', '回调与对账'],
        icon: 'credit-card'
      },
      {
        title: '小程序',
        description: '将接入平台登录、手机号和订阅消息等常用接口。',
        details: ['登录授权', '手机号', '订阅消息'],
        icon: 'smartphone'
      },
      {
        title: 'IoT',
        description: '将识别设备、接收遥测数据，并向在线设备发送控制指令。',
        details: ['设备身份', '遥测数据', '下行指令'],
        icon: 'radio-tower'
      }
    ]
  }
]
