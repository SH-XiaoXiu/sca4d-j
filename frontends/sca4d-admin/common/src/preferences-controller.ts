import type { AdminApiClient } from './api-client';
import type { JsonValue, Preference } from './types';

/** 携带强 ETag 的当前 Principal 偏好快照。 */
export interface VersionedPreference<T extends JsonValue = JsonValue> {
  readonly etag: string;
  readonly preference: Preference<T>;
}

/** 读取、完整替换和恢复当前 Principal 的已注册个人偏好。 */
export class PreferencesController {
  constructor(private readonly api: AdminApiClient) {}

  /** 读取一个完整偏好和当前强 ETag。 */
  async require<T extends JsonValue = JsonValue>(
    key: string,
  ): Promise<VersionedPreference<T>> {
    const result = await this.api.get<Preference<T>>(preferencePath(key));
    return versioned(result.data, result.etag);
  }

  /** 按已读取的强 ETag 完整替换一个偏好。 */
  async replace<T extends JsonValue = JsonValue>(
    key: string,
    value: T,
    etag: string,
  ): Promise<VersionedPreference<T>> {
    const result = await this.api.put<Preference<T>>(
      preferencePath(key),
      { value },
      { 'If-Match': etag },
    );
    return versioned(result.data, result.etag);
  }

  /** 按已读取的强 ETag 删除偏好覆盖值并恢复默认值。 */
  async reset<T extends JsonValue = JsonValue>(
    key: string,
    etag: string,
  ): Promise<VersionedPreference<T>> {
    const result = await this.api.delete<Preference<T>>(preferencePath(key), {
      'If-Match': etag,
    });
    return versioned(result.data, result.etag);
  }
}

/** 编码一个偏好键对应的路径。 */
function preferencePath(key: string): string {
  return `/me/preferences/${encodeURIComponent(key)}`;
}

/** 确认偏好响应发布了后续写入必须使用的 ETag。 */
function versioned<T extends JsonValue>(
  preference: Preference<T>,
  etag: null | string,
): VersionedPreference<T> {
  if (!etag) throw new Error('Preference response has no ETag');
  return Object.freeze({
    etag,
    preference: Object.freeze({ ...preference }),
  });
}
