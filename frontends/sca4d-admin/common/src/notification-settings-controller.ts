import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type { Int64String, LocaleCatalog } from './types';

import { ObservableController } from './controller';
import { AdminError, resourcePhaseForError } from './errors';
import { freezeLocaleCatalog } from './localized-fields';

/** Account 的通知语言、区域和可选渠道；各自使用独立 Version。 */
export interface NotificationSettings {
  readonly channels: Readonly<
    Record<string, Readonly<Record<string, boolean>>>
  >;
  readonly channelsVersion: Int64String;
  readonly definitions: Readonly<
    Record<
      string,
      Readonly<
        Record<
          string,
          { readonly defaultEnabled?: boolean; readonly required: boolean }
        >
      >
    >
  >;
  readonly languageTag: string;
  readonly languageVersion: Int64String;
  readonly regionCode: string;
  readonly regionVersion: Int64String;
}

/** 通知设置的读取状态，不保存页面的未提交草稿。 */
export interface NotificationSettingsState {
  readonly error: AdminError | null;
  readonly localeCatalog: LocaleCatalog | null;
  readonly phase: ResourcePhase;
  readonly settings: NotificationSettings | null;
}

/** 按用户读取的 Version 修改通知偏好，每次写入立即记录成功结果。 */
export class NotificationSettingsController extends ObservableController<NotificationSettingsState> {
  constructor(private readonly api: AdminApiClient) {
    super({ error: null, phase: 'idle', localeCatalog: null, settings: null });
  }

  /** 读取当前 Account 的通知偏好及各自版本。 */
  async load(): Promise<void> {
    const read = this.beginRead('settings');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const [result, locales] = await Promise.all([
        this.api.get<NotificationSettings>('/me/notification-settings'),
        this.api.get<LocaleCatalog>('/system/locales'),
      ]);
      if (this.isCurrentRead('settings', read))
        this.publish({
          error: null,
          phase: 'ready',
          localeCatalog: freezeLocaleCatalog(locales.data),
          settings: Object.freeze(result.data),
        });
    } catch (error) {
      if (!this.isCurrentRead('settings', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 完整替换可选渠道覆盖值，保持后端发布的渠道编码。 */
  async replaceChannels(
    channels: NotificationSettings['channels'],
  ): Promise<void> {
    const current = this.requireSettings();
    await this.replace('channels', channels, current.channelsVersion);
  }

  /** 使用已读取的语言 Version 替换通知语言，不重试覆盖并发修改。 */
  async replaceLanguage(languageTag: string): Promise<void> {
    const current = this.requireSettings();
    await this.replace('language', languageTag, current.languageVersion);
  }

  /** 使用已读取的区域 Version 替换业务区域。 */
  async replaceRegion(regionCode: string): Promise<void> {
    const current = this.requireSettings();
    await this.replace('region', regionCode, current.regionVersion);
  }

  /** 保留此前已经提交成功的字段和最新版本，供用户继续处理其他字段。 */
  private fail(error: unknown): void {
    const value =
      error instanceof AdminError
        ? error
        : new AdminError('unknown', 'Request failed');
    this.publish({
      ...this.state,
      error: value,
      phase: resourcePhaseForError(value),
    });
  }

  /** 三个设置使用相同请求外壳，但分别声明路径和值及其版本。 */
  private async replace(
    field: 'channels' | 'language' | 'region',
    value: unknown,
    version: Int64String,
  ): Promise<void> {
    const read = this.beginRead('settings');
    try {
      const result = await this.api.put<NotificationSettings>(
        `/me/notification-settings/${field}`,
        { value },
        { 'If-Match': `"${version}"` },
      );
      if (this.isCurrentRead('settings', read))
        this.publish({
          ...this.state,
          error: null,
          phase: 'ready',
          settings: Object.freeze(result.data),
        });
    } catch (error) {
      if (this.isCurrentRead('settings', read)) this.fail(error);
      throw error;
    }
  }

  /** 缺少资源快照时停止修改。 */
  private requireSettings(): NotificationSettings {
    if (!this.state.settings)
      throw new AdminError(
        'precondition',
        'Notification settings are not loaded',
      );
    return this.state.settings;
  }
}
