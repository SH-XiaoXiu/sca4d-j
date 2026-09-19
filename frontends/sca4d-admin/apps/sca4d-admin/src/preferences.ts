import { defineOverridesPreferences } from '@vben/preferences';

/** Sca4d-J 示例覆盖的 Vben 偏好设置。 */
export const overridesPreferences = defineOverridesPreferences({
  app: {
    accessMode: 'frontend',
    defaultAvatar: '/default-avatar.svg',
    defaultHomePath: '/profile',
    enableCheckUpdates: false,
    enableRefreshToken: false,
    name: import.meta.env.VITE_APP_TITLE,
  },
  copyright: {
    companyName: 'Sca4d-J',
    companySiteLink: '',
    date: '2026',
    enable: true,
  },
  logo: {
    enable: true,
  },
  shortcutKeys: {
    globalLockScreen: false,
  },
  theme: {
    mode: 'light',
  },
  widget: {
    lockScreen: false,
    notification: true,
  },
});
