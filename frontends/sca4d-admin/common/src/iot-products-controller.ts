import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type { Int64String, LocaleCatalog, LocalizedFields } from './types';

import { ObservableController } from './controller';
import { SavedResourceRefreshError } from './errors';
import { AdminError } from './errors';
import { freezeLocaleCatalog, freezeLocalizedFields } from './localized-fields';

/** IoT Product 在具体应用中的启停状态。 */
export type IotProductStatus = 'DISABLED' | 'ENABLED';

/** IoT Product 管理目录中的公共字段。 */
export interface IotProductCatalogItemBase {
  readonly accessMethods: readonly string[];
  readonly code: string;
  readonly displayName: string;
  readonly protocolCodes: readonly string[];
}

/** 由应用代码提供且运行时只读的 IoT Product。 */
export interface IotCodeProductCatalogItem extends IotProductCatalogItemBase {
  readonly createdAt: null;
  readonly id: null;
  readonly source: 'CODE';
  readonly status: null;
  readonly updatedAt: null;
}

/** 保存到数据库且允许管理端维护的 IoT Product。 */
export interface IotDatabaseProductCatalogItem extends IotProductCatalogItemBase {
  readonly createdAt: string;
  readonly id: Int64String;
  readonly source: 'DATABASE';
  readonly status: IotProductStatus;
  readonly updatedAt: string;
}

/** 管理端可以读取的任意来源 IoT Product。 */
export type IotProductCatalogItem =
  | IotCodeProductCatalogItem
  | IotDatabaseProductCatalogItem;

/** IoT Product 页面初始化所需的产品和可选技术定义。 */
export interface IotProductCatalog {
  readonly accessMethods: readonly string[];
  /** 后端返回的从一开始的当前页码。 */
  readonly page: number;
  readonly products: readonly IotProductCatalogItem[];
  readonly protocolCodes: readonly string[];
  /** 后端实际使用的单页数量上限。 */
  readonly size: number;
  /** 两种来源 Product 的总数量。 */
  readonly total: number;
}

/** 递归物模型值类型及当前请求语言下的嵌套展示名称。 */
export interface IotThingValueType {
  readonly elementType: IotThingValueType | null;
  readonly enumValues: readonly IotThingEnumValue[];
  readonly kind: string;
  readonly maximum: null | string;
  readonly maximumItems: null | number;
  readonly maximumLength: null | number;
  readonly minimum: null | string;
  readonly optionalFields: readonly IotThingField[];
  /** Java 正则完整匹配表达式；null 表示不限制字符串格式。 */
  readonly pattern: null | string;
  readonly requiredFields: readonly IotThingField[];
  readonly scale: null | number;
  readonly unit: null | string;
}

/** 物模型 enum 值及当前请求语言下的名称。 */
export interface IotThingEnumValue {
  readonly code: string;
  readonly displayName: string;
}

/** 事件输出、命令输入或 object 成员。 */
export interface IotThingField extends IotThingEnumValue {
  readonly valueType: IotThingValueType;
}

/** 物模型属性及其写入和遥测约束。 */
export interface IotThingProperty extends IotThingField {
  readonly access: string;
  readonly acknowledgementPolicy: null | string;
  readonly retryPolicy: null | string;
  readonly telemetry: boolean;
}

/** 物模型事件及本地化输出字段。 */
export interface IotThingEvent extends IotThingEnumValue {
  readonly delivery: string;
  readonly outputs: readonly IotThingField[];
}

/** 物模型命令及本地化输入字段。 */
export interface IotThingCommand extends IotThingEnumValue {
  readonly acknowledgementPolicy: string;
  readonly inputs: readonly IotThingField[];
  readonly retryPolicy: string;
}

/** 已发布物模型的技术约束和当前请求语言展示结果。 */
export interface IotThingModel {
  readonly commands: readonly IotThingCommand[];
  readonly displayName: string;
  readonly events: readonly IotThingEvent[];
  readonly formatVersion: number;
  readonly productCode: string;
  readonly properties: readonly IotThingProperty[];
  readonly version: number;
}

/** Product 完整动态多语言字段。 */
export interface IotProductI18n {
  readonly i18n: LocalizedFields;
  readonly productId: Int64String;
}

/** 一个数据库物模型版本的完整动态多语言字段。 */
export interface IotThingModelI18n {
  readonly i18n: LocalizedFields;
  readonly productCode: string;
  readonly version: number;
}

/** 创建数据库 Product 的完整输入。 */
export interface CreateIotProductInput {
  readonly accessMethods: readonly string[];
  readonly code: string;
  readonly i18n: LocalizedFields;
  readonly protocolCodes: readonly string[];
}

/** 修改停用数据库 Product 技术定义的完整输入。 */
export interface UpdateIotProductInput {
  readonly accessMethods: readonly string[];
  readonly protocolCodes: readonly string[];
}

/** 发布数据库物模型技术 JSON 和初始翻译的输入。 */
export interface PublishIotThingModelInput {
  readonly i18n: LocalizedFields;
  readonly model: Readonly<Record<string, unknown>>;
}

/** IoT Product 管理页面共享的不可变状态。 */
export interface IotProductsState {
  readonly catalog: IotProductCatalog | null;
  readonly detailsPhase: ResourcePhase;
  readonly error: AdminError | null;
  readonly localeCatalog: LocaleCatalog | null;
  readonly phase: ResourcePhase;
  readonly productI18n: IotProductI18n | null;
  readonly selectedThingModel: IotThingModel | null;
  readonly thingModelI18n: IotThingModelI18n | null;
  readonly thingModels: readonly IotThingModel[];
}

/** 管理双来源 Product、数据库写操作和物模型多语言字段。 */
export class IotProductsController extends ObservableController<IotProductsState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      catalog: null,
      detailsPhase: 'idle',
      error: null,
      localeCatalog: null,
      phase: 'idle',
      productI18n: null,
      selectedThingModel: null,
      thingModelI18n: null,
      thingModels: Object.freeze([]),
    });
  }

  /** 修改数据库 Product 启停状态并重新读取目录。 */
  async changeStatus(
    product: IotDatabaseProductCatalogItem,
    status: IotProductStatus,
  ): Promise<void> {
    try {
      await this.api.patch(`/iot/products/${product.id}/status`, { status });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 清除离开 Product 详情后不再使用的物模型和翻译状态。 */
  clearDetails(): void {
    this.cancelRead('loadThingModels');
    this.cancelRead('loadThingModel');
    this.cancelRead('loadThingModelI18n');
    this.publish({
      ...this.state,
      detailsPhase: 'idle',
      error: null,
      productI18n: null,
      selectedThingModel: null,
      thingModelI18n: null,
      thingModels: Object.freeze([]),
    });
  }

  /** 创建数据库 Product 并重新读取管理目录。 */
  async create(input: CreateIotProductInput): Promise<void> {
    try {
      await this.api.post('/iot/products', input);
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 同时读取指定页 Product 目录、表单技术选项和应用 Locale；未传分页时沿用当前页。 */
  async load(
    page = this.state.catalog?.page ?? 1,
    size = this.state.catalog?.size ?? 20,
  ): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const [catalog, locales] = await Promise.all([
        this.api.get<IotProductCatalog>(
          `/iot/products/catalog?page=${page}&size=${size}`,
        ),
        this.api.get<LocaleCatalog>('/system/locales'),
      ]);
      const value = freezeCatalog(catalog.data);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        catalog: value,
        error: null,
        localeCatalog: freezeLocaleCatalog(locales.data),
        phase: value.products.length > 0 ? 'ready' : 'empty',
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error, false);
      throw error;
    }
  }

  /** 读取数据库 Product 保存的全部 Locale 展示字段。 */
  async loadProductI18n(product: IotDatabaseProductCatalogItem): Promise<void> {
    const read = this.beginRead('loadProductI18n');
    try {
      const result = await this.api.get<IotProductI18n>(
        `/iot/products/${product.id}/i18n`,
      );
      if (!this.isCurrentRead('loadProductI18n', read)) return;
      this.publish({
        ...this.state,
        error: null,
        productI18n: freezeProductI18n(result.data),
      });
    } catch (error) {
      if (!this.isCurrentRead('loadProductI18n', read)) return;
      this.failMutation(error);
      throw error;
    }
  }

  /** 读取任意来源 Product 的指定物模型版本。 */
  async loadThingModel(productCode: string, version: number): Promise<void> {
    const read = this.beginRead('loadThingModel');
    this.publish({ ...this.state, detailsPhase: 'loading', error: null });
    try {
      const result = await this.api.get<IotThingModel>(
        `${catalogProductPath(productCode)}/thing-models/${version}`,
      );
      if (!this.isCurrentRead('loadThingModel', read)) return;
      this.publish({
        ...this.state,
        detailsPhase: 'ready',
        error: null,
        selectedThingModel: freezeThingModel(result.data),
      });
    } catch (error) {
      if (!this.isCurrentRead('loadThingModel', read)) return;
      this.fail(error, true);
      throw error;
    }
  }

  /** 读取数据库 Product 指定物模型版本的全部 Locale 展示字段。 */
  async loadThingModelI18n(
    product: IotDatabaseProductCatalogItem,
    version: number,
  ): Promise<void> {
    const read = this.beginRead('loadThingModelI18n');
    try {
      const result = await this.api.get<IotThingModelI18n>(
        `/iot/products/${product.id}/thing-models/${version}/i18n`,
      );
      if (!this.isCurrentRead('loadThingModelI18n', read)) return;
      this.publish({
        ...this.state,
        error: null,
        thingModelI18n: freezeThingModelI18n(result.data),
      });
    } catch (error) {
      if (!this.isCurrentRead('loadThingModelI18n', read)) return;
      this.failMutation(error);
      throw error;
    }
  }

  /** 读取任意来源 Product 的全部物模型版本。 */
  async loadThingModels(productCode: string): Promise<void> {
    const read = this.beginRead('loadThingModels');
    this.publish({
      ...this.state,
      detailsPhase: 'loading',
      error: null,
      productI18n: null,
      selectedThingModel: null,
      thingModelI18n: null,
    });
    try {
      const result = await this.api.get<readonly IotThingModel[]>(
        `${catalogProductPath(productCode)}/thing-models`,
      );
      const models = Object.freeze(
        result.data.map((item) => freezeThingModel(item)),
      );
      if (!this.isCurrentRead('loadThingModels', read)) return;
      this.publish({
        ...this.state,
        detailsPhase: models.length > 0 ? 'ready' : 'empty',
        error: null,
        thingModels: models,
      });
    } catch (error) {
      if (!this.isCurrentRead('loadThingModels', read)) return;
      this.fail(error, true);
      throw error;
    }
  }

  /** 发布数据库 Product 的新物模型版本并刷新版本列表。 */
  async publishThingModel(
    product: IotDatabaseProductCatalogItem,
    input: PublishIotThingModelInput,
  ): Promise<void> {
    try {
      const result = await this.api.post<IotThingModel>(
        `/iot/products/${product.id}/thing-models`,
        input,
      );
      try {
        await this.loadThingModels(product.code);
      } catch {
        throw new SavedResourceRefreshError();
      }
      this.publish({
        ...this.state,
        detailsPhase: 'ready',
        selectedThingModel: freezeThingModel(result.data),
      });
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 完整替换数据库 Product 多语言字段并重新读取目录和原值。 */
  async replaceProductI18n(
    product: IotDatabaseProductCatalogItem,
    i18n: LocalizedFields,
  ): Promise<void> {
    try {
      const result = await this.api.put<IotProductI18n>(
        `/iot/products/${product.id}/i18n`,
        { i18n },
      );
      this.publish({
        ...this.state,
        error: null,
        productI18n: freezeProductI18n(result.data),
      });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 完整替换数据库物模型版本的多语言字段并刷新当前详情。 */
  async replaceThingModelI18n(
    product: IotDatabaseProductCatalogItem,
    version: number,
    i18n: LocalizedFields,
  ): Promise<void> {
    try {
      const result = await this.api.put<IotThingModelI18n>(
        `/iot/products/${product.id}/thing-models/${version}/i18n`,
        { i18n },
      );
      this.publish({
        ...this.state,
        error: null,
        thingModelI18n: freezeThingModelI18n(result.data),
      });
      try {
        await this.loadThingModel(product.code, version);
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 修改停用数据库 Product 的接入方式和协议并重新读取目录。 */
  async update(
    product: IotDatabaseProductCatalogItem,
    input: UpdateIotProductInput,
  ): Promise<void> {
    try {
      await this.api.put(`/iot/products/${product.id}`, input);
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 将统一 HTTP 错误转换成目录或详情区域可以显示的阶段。 */
  private fail(error: unknown, details: boolean): void {
    const value = asAdminError(error);
    const phase = value.kind === 'permission' ? 'forbidden' : 'error';
    this.publish(
      details
        ? { ...this.state, detailsPhase: phase, error: value }
        : { ...this.state, phase, error: value },
    );
  }

  /** 保留已加载内容，只把写操作失败交给表单提示。 */
  private failMutation(error: unknown): void {
    this.publish({ ...this.state, error: asAdminError(error) });
  }
}

function catalogProductPath(productCode: string): string {
  return `/iot/products/catalog/${encodeURIComponent(productCode)}`;
}

function freezeCatalog(value: IotProductCatalog): IotProductCatalog {
  return Object.freeze({
    accessMethods: Object.freeze([...value.accessMethods]),
    page: value.page,
    products: Object.freeze(
      value.products.map((product) =>
        Object.freeze({
          ...product,
          accessMethods: Object.freeze([...product.accessMethods]),
          protocolCodes: Object.freeze([...product.protocolCodes]),
        }),
      ),
    ),
    protocolCodes: Object.freeze([...value.protocolCodes]),
    size: value.size,
    total: value.total,
  });
}

function freezeProductI18n(value: IotProductI18n): IotProductI18n {
  return Object.freeze({ ...value, i18n: freezeLocalizedFields(value.i18n) });
}

function freezeThingModelI18n(value: IotThingModelI18n): IotThingModelI18n {
  return Object.freeze({ ...value, i18n: freezeLocalizedFields(value.i18n) });
}

function freezeThingModel(value: IotThingModel): IotThingModel {
  return Object.freeze({
    ...value,
    commands: Object.freeze(
      value.commands.map((command) =>
        Object.freeze({
          ...command,
          inputs: Object.freeze(
            command.inputs.map((item) => freezeThingField(item)),
          ),
        }),
      ),
    ),
    events: Object.freeze(
      value.events.map((event) =>
        Object.freeze({
          ...event,
          outputs: Object.freeze(
            event.outputs.map((item) => freezeThingField(item)),
          ),
        }),
      ),
    ),
    properties: Object.freeze(
      value.properties.map((property) =>
        Object.freeze({
          ...property,
          valueType: freezeThingValueType(property.valueType),
        }),
      ),
    ),
  });
}

function freezeThingField(value: IotThingField): IotThingField {
  return Object.freeze({
    ...value,
    valueType: freezeThingValueType(value.valueType),
  });
}

function freezeThingValueType(value: IotThingValueType): IotThingValueType {
  return Object.freeze({
    ...value,
    elementType: value.elementType
      ? freezeThingValueType(value.elementType)
      : null,
    enumValues: Object.freeze(
      value.enumValues.map((item) => Object.freeze({ ...item })),
    ),
    optionalFields: Object.freeze(
      value.optionalFields.map((item) => freezeThingField(item)),
    ),
    requiredFields: Object.freeze(
      value.requiredFields.map((item) => freezeThingField(item)),
    ),
  });
}

function asAdminError(error: unknown): AdminError {
  return error instanceof AdminError
    ? error
    : new AdminError('unknown', 'Unexpected client error');
}
