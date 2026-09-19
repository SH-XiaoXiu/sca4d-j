import type { HttpRequest, HttpResponse } from './ports';
import type {
  IotDatabaseProductCatalogItem,
  IotProductCatalog,
  IotThingModel,
} from './iot-products-controller';

import { describe, expect, it } from 'vitest';

import { AdminApiClient } from './api-client';
import { IotProductsController } from './iot-products-controller';

const DATABASE_PRODUCT: IotDatabaseProductCatalogItem = {
  accessMethods: ['account:shared-secret'],
  code: 'gateway',
  createdAt: '2026-08-17T00:00:00Z',
  displayName: '网关',
  id: '41',
  protocolCodes: ['gateway-v1'],
  source: 'DATABASE',
  status: 'DISABLED',
  updatedAt: '2026-08-17T00:00:00Z',
};

const CATALOG: IotProductCatalog = {
  accessMethods: ['account:shared-secret', 'anonymous'],
  page: 1,
  products: [
    DATABASE_PRODUCT,
    {
      accessMethods: ['anonymous'],
      code: 'luminaire',
      createdAt: null,
      displayName: '应急灯具',
      id: null,
      protocolCodes: ['luminaire-v1'],
      source: 'CODE',
      status: null,
      updatedAt: null,
    },
  ],
  protocolCodes: ['gateway-v1', 'luminaire-v1'],
  size: 20,
  total: 2,
};

const MODEL: IotThingModel = {
  commands: [],
  displayName: '网关物模型',
  events: [],
  formatVersion: 2,
  productCode: 'gateway',
  properties: [],
  version: 1,
};

describe('iotProductsController', () => {
  it('同时读取双来源目录、技术选项、Locale 和统一物模型查询入口', async () => {
    const requests: HttpRequest[] = [];
    const controller = new IotProductsController(
      api(requests, [
        ok(CATALOG),
        ok({ defaultLocale: 'zh-CN', supportedLocales: ['zh-CN', 'en-US'] }),
        ok([MODEL]),
        ok(MODEL),
      ]),
    );

    await controller.load();
    await controller.loadThingModels('gateway');
    await controller.loadThingModel('gateway', 1);

    expect(requests.map((request) => request.path)).toEqual([
      '/iot/products/catalog?page=1&size=20',
      '/system/locales',
      '/iot/products/catalog/gateway/thing-models',
      '/iot/products/catalog/gateway/thing-models/1',
    ]);
    expect(controller.state.catalog?.products).toHaveLength(2);
    expect(controller.state.localeCatalog?.defaultLocale).toBe('zh-CN');
    expect(controller.state.selectedThingModel?.displayName).toBe('网关物模型');
    expect(Object.isFrozen(controller.state.thingModels)).toBe(true);
  });

  it('数据库 Product 修改和物模型发布后刷新对应读取状态', async () => {
    const requests: HttpRequest[] = [];
    const controller = new IotProductsController(
      api(requests, [
        ok(DATABASE_PRODUCT),
        ok(CATALOG),
        ok({ defaultLocale: 'zh-CN', supportedLocales: ['zh-CN'] }),
        ok(MODEL),
        ok([MODEL]),
      ]),
    );

    await controller.update(DATABASE_PRODUCT, {
      accessMethods: [],
      protocolCodes: ['gateway-v1'],
    });
    await controller.publishThingModel(DATABASE_PRODUCT, {
      i18n: { 'zh-CN': { '/displayName': '网关物模型' } },
      model: { formatVersion: 2, productCode: 'gateway', version: 1 },
    });

    expect(requests.map((request) => request.path)).toEqual([
      '/iot/products/41',
      '/iot/products/catalog?page=1&size=20',
      '/system/locales',
      '/iot/products/41/thing-models',
      '/iot/products/catalog/gateway/thing-models',
    ]);
    expect(requests[0]?.body).toEqual({
      accessMethods: [],
      protocolCodes: ['gateway-v1'],
    });
    expect(controller.state.selectedThingModel?.version).toBe(1);
  });
});

function api(
  requests: HttpRequest[],
  responses: HttpResponse[],
): AdminApiClient {
  return new AdminApiClient(
    {
      request: async (request) => {
        requests.push(request);
        return responses.shift() ?? { status: 500, headers: {} };
      },
    },
    {
      clear: () => undefined,
      read: () => ({
        accessToken: 'app-token',
        accessTokenExpiresAt: '2026-08-17T01:00:00Z',
        sessionExpiresAt: '2026-08-17T02:00:00Z',
        sessionId: 'session',
        sessionToken: null,
        tokenMode: 'SESSION',
        tokenType: 'Bearer',
      }),
      write: () => undefined,
    },
    { read: () => 'zh-CN', write: () => undefined },
    () => undefined,
  );
}

function ok(data: unknown): HttpResponse {
  return { body: { code: '0', data }, headers: {}, status: 200 };
}
