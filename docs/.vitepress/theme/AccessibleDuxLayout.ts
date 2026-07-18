import { Fragment, defineComponent, h, nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vitepress'
import DuxTheme from '@duxweb/vitepress-theme'

function buttonWithIcon(root: ParentNode, iconClass: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((button) =>
    Array.from(button.querySelectorAll<HTMLElement>('*')).some((element) => element.classList.contains(iconClass))
  )
}

export default defineComponent({
  name: 'AccessibleDuxLayout',
  setup() {
    const route = useRoute()
    const cleanups: Array<() => void> = []

    function clearListeners() {
      cleanups.splice(0).forEach((cleanup) => cleanup())
    }

    async function enhanceThemeMarkup() {
      if (typeof document === 'undefined') return
      await nextTick()
      clearListeners()

      const main = document.querySelector<HTMLElement>('main')
      main?.setAttribute('id', 'main-content')
      main?.setAttribute('tabindex', '-1')

      const mobileMenu = document.getElementById('mobile-menu')
      const header = document.querySelector('header')
      if (mobileMenu && header) {
        const toggle = buttonWithIcon(header, 'icon-[heroicons--bars-3]')
        if (toggle) {
          toggle.setAttribute('aria-label', '打开导航菜单')
          toggle.setAttribute('aria-controls', 'mobile-menu')
          const updateExpanded = () => {
            window.setTimeout(() => {
              toggle.setAttribute('aria-expanded', String(!mobileMenu.classList.contains('hidden')))
            })
          }
          updateExpanded()
          toggle.addEventListener('click', updateExpanded)
          cleanups.push(() => toggle.removeEventListener('click', updateExpanded))
        }
      }

      const closeSidebar = buttonWithIcon(document, 'icon-[heroicons--x-mark]')
      closeSidebar?.setAttribute('aria-label', '关闭文档目录')
    }

    onMounted(enhanceThemeMarkup)
    watch(() => route.path, enhanceThemeMarkup)
    onBeforeUnmount(clearListeners)

    return () => h(Fragment, [
      h('a', { class: 'skip-link', href: '#main-content' }, '跳到正文'),
      h(DuxTheme.Layout)
    ])
  }
})
