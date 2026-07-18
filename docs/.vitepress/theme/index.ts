import type { Theme } from 'vitepress'
import DuxTheme from '@duxweb/vitepress-theme'
import '@duxweb/vitepress-theme/dist/index.css'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import AccessibleDuxLayout from './AccessibleDuxLayout'
import HomeOverview from './HomeOverview.vue'
import './custom.css'

export default {
  ...DuxTheme,
  Layout: AccessibleDuxLayout,
  enhanceApp(context) {
    DuxTheme.enhanceApp?.(context)
    context.app.component('HomeOverview', HomeOverview)
  }
} satisfies Theme
