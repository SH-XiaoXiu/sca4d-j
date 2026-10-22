import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type {
  CreateDictionaryInput,
  CreateDictionaryOptionInput,
  DictionarySelection,
  LocaleCatalog,
  ManagedDictionary,
  ManagedDictionaryOption,
  SystemDictionaries,
  SystemSetting,
  SystemSettingGroup,
  SystemSettings,
  UpdateDictionaryInput,
  UpdateDictionaryOptionInput,
} from './types';

import { ObservableController } from './controller';
import { SavedResourceRefreshError } from './errors';
import { AdminError } from './errors';
import { freezeLocaleCatalog, freezeLocalizedFields } from './localized-fields';

/** 系统字典页面共享的不可变状态。 */
export interface SystemDictionariesState {
  readonly dictionaries: readonly ManagedDictionary[];
  readonly error: AdminError | null;
  readonly localeCatalog: LocaleCatalog | null;
  readonly phase: ResourcePhase;
  readonly selections: Readonly<Record<string, DictionarySelection>>;
}

/** 系统设置页面共享的不可变状态。 */
export interface SystemSettingsState {
  readonly error: AdminError | null;
  readonly groups: readonly SystemSettingGroup[];
  readonly phase: ResourcePhase;
}

/** 管理系统字典、数据库项和业务页面所需的聚合选择项。 */
export class SystemDictionariesController extends ObservableController<SystemDictionariesState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      dictionaries: Object.freeze([]),
      error: null,
      localeCatalog: null,
      phase: 'idle',
      selections: Object.freeze({}),
    });
  }

  /** 创建动态字典并使用接口返回的完整管理结果更新页面。 */
  async create(input: CreateDictionaryInput): Promise<void> {
    try {
      const result = await this.api.post<SystemDictionaries>(
        '/system/dictionaries',
        input,
      );
      this.replaceDictionaries(result.data);
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 给动态字典或可扩展代码字典创建数据库选项。 */
  async createOption(
    dictionaryKey: string,
    input: CreateDictionaryOptionInput,
  ): Promise<void> {
    try {
      const result = await this.api.post<SystemDictionaries>(
        `${dictionaryPath(dictionaryKey)}/options`,
        input,
      );
      this.replaceDictionaries(result.data);
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 按客户端读取到的版本删除动态字典。 */
  async delete(dictionary: ManagedDictionary): Promise<void> {
    if (dictionary.version === null) {
      throw new Error('Code dictionaries cannot be deleted');
    }
    try {
      await this.api.delete(
        `${dictionaryPath(dictionary.key)}?version=${encodeURIComponent(dictionary.version)}`,
      );
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

  /** 按客户端读取到的版本删除一个数据库字典项。 */
  async deleteOption(
    dictionary: ManagedDictionary,
    option: ManagedDictionaryOption,
  ): Promise<void> {
    if (option.version === null) {
      throw new Error('Code dictionary options cannot be deleted');
    }
    try {
      await this.api.delete(
        `${dictionaryPath(dictionary.key)}/options/${encodeURIComponent(option.value)}?version=${encodeURIComponent(option.version)}`,
      );
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

  /** 读取所有代码字典、动态字典及其选项。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const [result, locales] = await Promise.all([
        this.api.get<SystemDictionaries>('/system/dictionaries'),
        this.api.get<LocaleCatalog>('/system/locales'),
      ]);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        dictionaries: freezeDictionaries(result.data),
        error: null,
        localeCatalog: freezeLocaleCatalog(locales.data),
        phase: result.data.dictionaries.length > 0 ? 'ready' : 'empty',
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 读取并缓存多个业务页面需要的聚合字典选择项。 */
  async loadSelections(
    keys: readonly string[],
  ): Promise<Readonly<Record<string, DictionarySelection>>> {
    try {
      const loaded = await Promise.all(
        keys.map(async (key) => {
          const result = await this.api.get<DictionarySelection>(
            `${dictionaryPath(key)}/options`,
          );
          return freezeSelection(result.data);
        }),
      );
      const selections = Object.freeze({
        ...this.state.selections,
        ...Object.fromEntries(
          loaded.map((selection) => [selection.key, selection]),
        ),
      });
      this.publish({ ...this.state, selections, error: null });
      return selections;
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 按客户端读取到的版本修改动态字典。 */
  async update(
    dictionary: ManagedDictionary,
    input: UpdateDictionaryInput,
  ): Promise<void> {
    try {
      const result = await this.api.put<SystemDictionaries>(
        dictionaryPath(dictionary.key),
        input,
      );
      this.replaceDictionaries(result.data);
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 按客户端读取到的版本修改一个数据库字典项。 */
  async updateOption(
    dictionary: ManagedDictionary,
    option: ManagedDictionaryOption,
    input: UpdateDictionaryOptionInput,
  ): Promise<void> {
    try {
      const result = await this.api.put<SystemDictionaries>(
        `${dictionaryPath(dictionary.key)}/options/${encodeURIComponent(option.value)}`,
        input,
      );
      this.replaceDictionaries(result.data);
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 将统一 HTTP 错误转换为页面可以展示的阶段。 */
  private fail(error: unknown): void {
    const value = asAdminError(error);
    let phase: ResourcePhase = 'error';
    if (value.kind === 'permission') phase = 'forbidden';
    else if (value.kind === 'versionConflict') phase = 'conflict';
    this.publish({ ...this.state, error: value, phase });
  }

  /** 保留已加载的字典列表，只把写操作错误交给表单提示。 */
  private failMutation(error: unknown): void {
    const value = asAdminError(error);
    const loadedPhase =
      this.state.phase === 'ready' || this.state.phase === 'empty'
        ? this.state.phase
        : null;
    let failurePhase: ResourcePhase = 'error';
    if (value.kind === 'permission') failurePhase = 'forbidden';
    else if (value.kind === 'versionConflict') failurePhase = 'conflict';
    this.publish({
      ...this.state,
      error: value,
      phase: loadedPhase ?? failurePhase,
    });
  }

  /** 使用接口结果替换字典列表，并保留已读的业务选择项缓存。 */
  private replaceDictionaries(data: SystemDictionaries): void {
    const dictionaries = freezeDictionaries(data);
    this.publish({
      ...this.state,
      dictionaries,
      error: null,
      phase: dictionaries.length > 0 ? 'ready' : 'empty',
    });
  }
}

/** 冻结字典及数据库选项携带的完整多语言字段。 */
function freezeDictionaries(
  data: SystemDictionaries,
): readonly ManagedDictionary[] {
  return Object.freeze(
    data.dictionaries.map((dictionary) =>
      Object.freeze({
        ...dictionary,
        options: Object.freeze(
          dictionary.options.map((option) =>
            Object.freeze({
              ...option,
              i18n: option.i18n ? freezeLocalizedFields(option.i18n) : null,
            }),
          ),
        ),
      }),
    ),
  );
}

/** 管理系统设置的读取、覆盖和恢复默认值流程。 */
export class SystemSettingsController extends ObservableController<SystemSettingsState> {
  constructor(private readonly api: AdminApiClient) {
    super({ error: null, groups: Object.freeze([]), phase: 'idle' });
  }

  /** 读取当前应用已注册的全部设置分组和当前值。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const result = await this.api.get<SystemSettings>('/system/settings');
      this.replaceSettings(result.data);
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 用 Codec 可解析的 JSON 值覆盖一个非只读设置。 */
  async replace(setting: SystemSetting, value: unknown): Promise<void> {
    try {
      const result = await this.api.put<SystemSettings>(
        `/system/settings/${encodeURIComponent(setting.key)}`,
        { value },
        { 'If-Match': `"${setting.version}"` },
      );
      this.replaceSettings(result.data);
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 删除数据库值，使指定设置恢复 Java 默认值。 */
  async reset(setting: SystemSetting): Promise<void> {
    try {
      const result = await this.api.delete<SystemSettings>(
        `/system/settings/${encodeURIComponent(setting.key)}`,
        { 'If-Match': `"${setting.version}"` },
      );
      this.replaceSettings(result.data);
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 将统一 HTTP 错误转换为页面可以展示的阶段。 */
  private fail(error: unknown): void {
    const value = asAdminError(error);
    let phase: ResourcePhase = 'error';
    if (value.kind === 'permission') phase = 'forbidden';
    else if (value.kind === 'versionConflict') phase = 'conflict';
    this.publish({ ...this.state, error: value, phase });
  }

  /** 使用接口结果替换设置分组，并冻结所有页面可见数据。 */
  private replaceSettings(data: SystemSettings): void {
    const groups = Object.freeze(
      data.groups.map((group) =>
        Object.freeze({
          ...group,
          settings: Object.freeze(
            group.settings.map((setting) => Object.freeze({ ...setting })),
          ),
        }),
      ),
    );
    this.publish({
      error: null,
      groups,
      phase: groups.length > 0 ? 'ready' : 'empty',
    });
  }
}

/** 编码一个路径中的字典键。 */
function dictionaryPath(key: string): string {
  return `/system/dictionaries/${encodeURIComponent(key)}`;
}

/** 冻结聚合字典选择项，避免页面在渲染时改写共享状态。 */
function freezeSelection(selection: DictionarySelection): DictionarySelection {
  return Object.freeze({
    ...selection,
    options: Object.freeze(
      selection.options.map((option) => Object.freeze({ ...option })),
    ),
  });
}

/** 保留统一 API 客户端已经转换过的错误，其余错误归一化为未知错误。 */
function asAdminError(error: unknown): AdminError {
  return error instanceof AdminError
    ? error
    : new AdminError('unknown', 'Request failed');
}
