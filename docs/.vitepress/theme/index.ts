import type { Theme } from 'vitepress'
import DuxTheme from '@duxweb/vitepress-theme'
import '@duxweb/vitepress-theme/dist/index.css'
import AccessibleDuxLayout from './AccessibleDuxLayout'
import './custom.css'

export default {
  ...DuxTheme,
  Layout: AccessibleDuxLayout
} satisfies Theme
