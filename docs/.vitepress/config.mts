import { createReadStream, existsSync, statSync } from 'node:fs'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { extname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { DefaultTheme } from 'vitepress'
import type { DuxThemeConfig } from '@duxweb/vitepress-theme'
import { withDuxTheme } from '@duxweb/vitepress-theme/config'

const javadocRoot = resolve(fileURLToPath(new URL('../public/javadoc/', import.meta.url)))
const javadocRootPrefix = `${javadocRoot}${sep}`
const javadocContentTypes: Record<string, string> = {
  '.css': 'text/css; charset=utf-8',
  '.gif': 'image/gif',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2'
}

type ViteDevServer = {
  middlewares: {
    use(
      handler: (
        request: IncomingMessage,
        response: ServerResponse,
        next: () => void
      ) => void
    ): void
  }
}

const javadocDevPlugin = {
  name: 'sca4dj-javadoc-dev',
  configureServer(server: ViteDevServer) {
    server.middlewares.use((request, response, next) => {
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        next()
        return
      }

      let pathname: string
      try {
        pathname = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname)
      } catch {
        response.statusCode = 400
        response.end()
        return
      }

      if (!pathname.startsWith('/javadoc/')) {
        next()
        return
      }

      let relativePath = pathname.slice('/javadoc/'.length)
      if (relativePath === '' || relativePath.endsWith('/')) {
        relativePath += 'index.html'
      }

      const file = resolve(javadocRoot, relativePath)
      if (!file.startsWith(javadocRootPrefix)) {
        response.statusCode = 403
        response.end()
        return
      }
      if (!existsSync(file) || !statSync(file).isFile()) {
        next()
        return
      }

      response.statusCode = 200
      response.setHeader(
        'Content-Type',
        javadocContentTypes[extname(file)] ?? 'application/octet-stream'
      )
      if (request.method === 'HEAD') {
        response.end()
        return
      }
      createReadStream(file).pipe(response)
    })
  }
}

const homeSidebar: DefaultTheme.SidebarItem[] = [
  {
    "text": "项目介绍",
    "link": "/"
  },
  {
    "text": "分布式单体",
    "link": "/home/architecture"
  },
  {
    "text": "项目结构",
    "link": "/home/project-structure"
  },
  {
    "text": "身份与账号",
    "link": "/home/identity"
  },
  {
    "text": "资源与权限",
    "link": "/home/permission"
  },
  {
    "text": "认证与 Session",
    "link": "/home/authentication-session"
  },
  {
    "text": "IoT 对象与生命周期",
    "link": "/home/iot"
  }
]

const tutorialsSidebar: DefaultTheme.SidebarItem[] = [
  {
    "text": "第一次运行",
    "link": "/tutorials/local-start"
  },
  {
    "text": "组装应用",
    "link": "/tutorials/assemble-application"
  },
  {
    "text": "开发后端业务接口",
    "link": "/tutorials/backend-feature"
  },
  {
    "text": "开发管理端页面",
    "link": "/tutorials/admin-feature"
  },
  {
    "text": "接入身份与权限",
    "link": "/tutorials/identity-and-permission"
  },
  {
    "text": "接入认证与 Session",
    "link": "/tutorials/authentication-and-session"
  },
  {
    "text": "字典与设置",
    "link": "/tutorials/settings-and-dictionaries"
  },
  {
    "text": "上传和引用文件",
    "link": "/tutorials/file-lifecycle"
  },
  {
    "text": "发布消息与通知",
    "link": "/tutorials/messaging-and-notification"
  },
  {
    "text": "执行和调度任务",
    "link": "/tutorials/jobs-and-scheduling"
  },
  {
    "text": "推送浏览器事件",
    "link": "/tutorials/realtime-events"
  },
  {
    "text": "部署 Gateway 与接入回调",
    "link": "/tutorials/gateway-and-callbacks"
  },
  {
    "text": "开放外部 API",
    "link": "/tutorials/external-api"
  },
  {
    "text": "接入第一台设备",
    "link": "/tutorials/iot-device-access"
  },
  {
    "text": "查询设备状态与下发命令",
    "link": "/tutorials/iot-state-and-commands"
  },
  {
    "text": "构建、部署与排障",
    "link": "/tutorials/build-deploy-troubleshoot"
  }
]

const standardsSidebar: DefaultTheme.SidebarItem[] = [
  {
    "text": "规范入口",
    "link": "/standards/"
  },
  {
    "text": "工程原则",
    "items": [
      { "text": "工程原则", "link": "/standards/01_principles" },
      { "text": "架构与边界", "link": "/standards/02_architecture" },
      { "text": "命名与代码结构", "link": "/standards/03_code-structure" },
      { "text": "Java 与 Spring", "link": "/standards/04_java-and-spring" }
    ]
  },
  {
    "text": "技术实现",
    "items": [
      { "text": "SQL 与持久化", "link": "/standards/05_persistence" },
      { "text": "数据库", "link": "/standards/06_database" },
      { "text": "Vue、TypeScript 与交互", "link": "/standards/07_frontend" },
      { "text": "注释、文档注释与日志", "link": "/standards/08_comments-and-logging" }
    ]
  },
  {
    "text": "构建与验收",
    "items": [
      { "text": "格式化与构建", "link": "/standards/09_build" },
      { "text": "Sca4d-J 构建 Gradle 插件", "link": "/standards/10_gradle-plugins" },
      { "text": "测试与验证", "link": "/standards/11_testing" },
      { "text": "代码评审", "link": "/standards/12_review" }
    ]
  },
  {
    "text": "文档",
    "items": [
      { "text": "文档规范", "link": "/standards/13_documentation" }
    ]
  }
]

const referenceSidebar: DefaultTheme.SidebarItem[] = [
  {
    "text": "参考入口与 Java API",
    "link": "/reference/"
  },
  {
    "text": "HTTP 通用契约",
    "link": "/reference/http/common"
  },
  {
    "text": "Sca4d Server HTTP API",
    "link": "/reference/http/sca4d-server"
  },
  {
    "text": "运行配置",
    "link": "/reference/runtime/sca4d-server"
  }
]

const requirementsSidebar: DefaultTheme.SidebarItem[] = [
  {
    "text": "需求与实现设计",
    "link": "/requirements/"
  }
]

const aiCollaborationSidebar: DefaultTheme.SidebarItem[] = [
  {
    "text": "AI 协作",
    "link": "/ai-collaboration/"
  }
]

const themeConfig = {
  logo: '/logo.png',
  siteTitle: 'Sca4d-J',
  nav: [
    { text: '首页', link: '/' },
    { text: '教程', link: '/tutorials/local-start' },
    { text: '项目规范', link: '/standards/' },
    { text: '参考', link: '/reference/' },
    { text: '需求文档', link: '/requirements/' },
    { text: 'AI 协作', link: '/ai-collaboration/' }
  ],
  socialLinks: [
    {
      icon: 'github',
      link: 'https://github.com/SH-XiaoXiu/sca4d-j',
      ariaLabel: 'Sca4d-J GitHub'
    }
  ],
  sidebar: {
    '/tutorials/': tutorialsSidebar,
    '/standards/': standardsSidebar,
    '/reference/': referenceSidebar,
    '/requirements/': requirementsSidebar,
    '/ai-collaboration/': aiCollaborationSidebar,
    '/': homeSidebar
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
  description: '可运行、可删减的 Java 项目模板',
  srcExclude: ['AGENTS.md'],
  cleanUrls: true,
  lastUpdated: true,
  ignoreDeadLinks: [/^\/javadoc\//],
  markdown: {
    config(md) {
      const renderLink = md.renderer.rules.link_open!
      md.renderer.rules.link_open = (tokens, index, options, env, renderer) => {
        if (tokens[index].attrGet('href')?.startsWith('/javadoc/')) {
          // Javadoc 使用独立 HTML，跳过 VitePress 路由和 index.html 地址转换。
          tokens[index].attrSet('target', '_self')
        }
        return renderLink(tokens, index, options, env, renderer)
      }
    }
  },
  vite: {
    plugins: [javadocDevPlugin]
  },
  head: [
    ['meta', { name: 'theme-color', content: '#15705f' }],
    ['link', { rel: 'icon', type: 'image/png', href: '/logo.png' }]
  ],
  themeConfig
})
