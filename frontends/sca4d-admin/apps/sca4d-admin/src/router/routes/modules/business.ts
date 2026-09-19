import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/** Sca4d Server 示例开放的业务页面。 */
const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:server-cog',
      order: 10,
      title: $t('page.business.server'),
    },
    name: 'Sca4dServer',
    path: '/sca4d-server',
    children: [
      {
        component: () => import('#/pages/AccountsPage.vue'),
        meta: {
          authority: ['platform:accounts:read', 'platform:accounts:manage'],
          icon: 'lucide:users',
          order: 1,
          title: $t('page.business.accounts'),
        },
        name: 'Accounts',
        path: 'accounts',
      },
      {
        component: () => import('#/pages/RolesPage.vue'),
        meta: {
          authority: ['platform:roles:read', 'platform:roles:manage'],
          icon: 'lucide:shield-check',
          order: 2,
          title: $t('page.business.roles'),
        },
        name: 'Roles',
        path: 'roles',
      },
      {
        component: () => import('#/pages/PermissionsPage.vue'),
        meta: {
          icon: 'lucide:key-round',
          order: 3,
          title: $t('page.business.permissions'),
        },
        name: 'Permissions',
        path: 'permissions',
      },
      {
        meta: {
          icon: 'lucide:settings-2',
          order: 4,
          title: $t('page.business.systemSettings'),
        },
        name: 'SystemConfiguration',
        path: 'system',
        children: [
          {
            component: () => import('#/pages/SystemSettingsPage.vue'),
            meta: {
              authority: ['system:settings:read', 'system:settings:manage'],
              icon: 'lucide:sliders-horizontal',
              order: 1,
              title: $t('page.business.basicSettings'),
            },
            name: 'SystemSettings',
            path: '/sca4d-server/settings',
          },
          {
            component: () => import('#/pages/SystemDictionariesPage.vue'),
            meta: {
              authority: [
                'system:dictionaries:read',
                'system:dictionaries:manage',
              ],
              icon: 'lucide:book-open-check',
              order: 2,
              title: $t('page.business.dictionaries'),
            },
            name: 'SystemDictionaries',
            path: '/sca4d-server/dictionaries',
          },
          {
            component: () => import('#/pages/FederatedProvidersPage.vue'),
            meta: {
              authority: ['platform:accounts:read', 'platform:accounts:manage'],
              icon: 'lucide:globe-2',
              order: 3,
              title: $t('page.business.federatedProviders'),
            },
            name: 'FederatedProviders',
            path: '/sca4d-server/federated-providers',
          },
        ],
      },
      {
        meta: {
          icon: 'lucide:bell',
          order: 5,
          title: $t('page.business.notificationSettings'),
        },
        name: 'NotificationConfiguration',
        path: 'notifications',
        children: [
          {
            component: () => import('#/pages/EmailNotificationsPage.vue'),
            meta: {
              authority: ['system:email:read', 'system:email:manage'],
              icon: 'lucide:mail',
              order: 1,
              title: $t('page.business.emailNotifications'),
            },
            name: 'EmailNotifications',
            path: '/sca4d-server/email',
          },
          {
            component: () => import('#/pages/SmsNotificationsPage.vue'),
            meta: {
              authority: ['system:sms:read', 'system:sms:manage'],
              icon: 'lucide:message-square-text',
              order: 2,
              title: $t('page.business.smsNotifications'),
            },
            name: 'SmsNotifications',
            path: '/sca4d-server/sms',
          },
        ],
      },
      {
        component: () => import('#/pages/OperationLogsPage.vue'),
        meta: {
          authority: ['system:audit:read'],
          icon: 'lucide:scroll-text',
          order: 6,
          title: $t('page.business.operationLogs'),
        },
        name: 'OperationLogs',
        path: 'operationlogs',
      },
      {
        component: () => import('#/pages/IotProductsPage.vue'),
        meta: {
          authority: ['iot:products:read', 'iot:products:manage'],
          icon: 'lucide:boxes',
          order: 7,
          title: $t('page.business.iotProducts'),
        },
        name: 'IotProducts',
        path: 'iot-products',
      },
    ],
  },
  {
    component: () => import('#/pages/ProfilePage.vue'),
    meta: {
      hideInMenu: true,
      icon: 'lucide:user-round',
      title: $t('page.business.profile'),
    },
    name: 'Profile',
    path: '/profile',
  },
  {
    component: () => import('#/pages/InboxPage.vue'),
    meta: {
      hideInMenu: true,
      icon: 'lucide:inbox',
      title: $t('page.business.inbox'),
    },
    name: 'Inbox',
    path: '/notifications',
  },
  {
    component: () => import('#/pages/SecurityPage.vue'),
    meta: {
      hideInMenu: true,
      icon: 'lucide:lock-keyhole',
      title: $t('page.business.security'),
    },
    name: 'Security',
    path: '/security',
  },
];

export default routes;
