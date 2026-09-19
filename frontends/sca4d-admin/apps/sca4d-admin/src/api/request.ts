import type { RequestClientOptions } from '@vben/request';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import {
  CLIENT_STATE_REFRESH_HEADER,
  requestsAuthenticationSessionRefresh,
} from '@sca4d/admin';
import { message } from 'ant-design-vue';

import { notifyAuthenticationSessionRefresh } from '#/auth/client-state-refresh';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

/** 创建能够读取 Sca4d-J `ApiResponse` 的请求客户端。 */
function createSca4dRequestClient(
  baseURL: string,
  authenticated: boolean,
  options?: RequestClientOptions,
) {
  const client = new RequestClient({ ...options, baseURL });

  client.addRequestInterceptor({
    fulfilled: async (config) => {
      config.headers['Accept-Language'] = preferences.app.locale;
      if (authenticated) {
        const accessToken = useAccessStore().accessToken;
        config.headers.Authorization = accessToken
          ? `Bearer ${accessToken}`
          : undefined;
      }
      return config;
    },
  });

  client.addResponseInterceptor({
    fulfilled: (response) => {
      const refresh = response.headers[CLIENT_STATE_REFRESH_HEADER];
      if (
        requestsAuthenticationSessionRefresh(
          typeof refresh === 'string' ? refresh : undefined,
        )
      ) {
        notifyAuthenticationSessionRefresh();
      }
      if (response.status === 204) {
        response.data = { code: '0', data: undefined };
      }
      return response;
    },
    rejected: (error) => {
      const refresh = error?.response?.headers?.[CLIENT_STATE_REFRESH_HEADER];
      if (
        requestsAuthenticationSessionRefresh(
          typeof refresh === 'string' ? refresh : undefined,
        )
      ) {
        notifyAuthenticationSessionRefresh();
      }
      throw error;
    },
  });

  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: '0',
    }),
  );

  client.addResponseInterceptor(
    errorMessageResponseInterceptor((fallbackMessage: string, error) => {
      const response = error?.response?.data ?? {};
      const backendMessage =
        typeof response.message === 'string' ? response.message : '';
      message.error(backendMessage || fallbackMessage);
    }),
  );

  return client;
}

/** 发送密码、TOTP、刷新和注销等自行指定 Token 的请求。 */
export const baseRequestClient = createSca4dRequestClient(apiURL, false, {
  responseReturn: 'data',
});

/** 使用当前 access Token 发送普通认证请求。 */
export const requestClient = createSca4dRequestClient(apiURL, true, {
  responseReturn: 'data',
});
