import { computed } from 'vue';

import { useAccessStore } from '@vben/stores';

import { useAuthStore } from '#/store/auth';
/** 从同一认证快照读取页面权限，不创建另一份授权状态。 */
export function useSjAccess() {
  const accessStore = useAccessStore();
  const authStore = useAuthStore();
  const session = computed(() => {
    const codes = new Set(accessStore.accessCodes);
    return {
      capabilities: {
        accountsManage: codes.has('PLATFORM:ACCOUNTS:MANAGE'),
        accountsRead:
          codes.has('PLATFORM:ACCOUNTS:READ') ||
          codes.has('PLATFORM:ACCOUNTS:MANAGE'),
        rolesManage: codes.has('PLATFORM:ROLES:MANAGE'),
        rolesRead:
          codes.has('PLATFORM:ROLES:READ') ||
          codes.has('PLATFORM:ROLES:MANAGE'),
        auditRead: codes.has('SYSTEM:AUDIT:READ'),
        dictionariesManage: codes.has('SYSTEM:DICTIONARIES:MANAGE'),
        dictionariesRead:
          codes.has('SYSTEM:DICTIONARIES:READ') ||
          codes.has('SYSTEM:DICTIONARIES:MANAGE'),
        emailManage: codes.has('SYSTEM:EMAIL:MANAGE'),
        emailRead:
          codes.has('SYSTEM:EMAIL:READ') || codes.has('SYSTEM:EMAIL:MANAGE'),
        smsManage: codes.has('SYSTEM:SMS:MANAGE'),
        smsRead: codes.has('SYSTEM:SMS:READ') || codes.has('SYSTEM:SMS:MANAGE'),
        settingsManage: codes.has('SYSTEM:SETTINGS:MANAGE'),
        settingsRead:
          codes.has('SYSTEM:SETTINGS:READ') ||
          codes.has('SYSTEM:SETTINGS:MANAGE'),
        federatedProvidersManage: codes.has('PLATFORM:ACCOUNTS:MANAGE'),
        federatedProvidersRead:
          codes.has('PLATFORM:ACCOUNTS:READ') ||
          codes.has('PLATFORM:ACCOUNTS:MANAGE'),
        iotProductsManage: codes.has('IOT:PRODUCTS:MANAGE'),
        iotProductsRead:
          codes.has('IOT:PRODUCTS:READ') || codes.has('IOT:PRODUCTS:MANAGE'),
      },
      session: authStore.currentSession,
    };
  });
  return session;
}
