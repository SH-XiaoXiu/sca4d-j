import type { AdminApiClient } from './api-client';
import type { ObjectTransferPort } from './ports';
import type { FileAccessUrl, FileUpload, Int64String } from './types';

import { AdminError, mapNetworkError } from './errors';

const ACCESS_URL_SAFETY_MILLISECONDS = 30_000;

/** 浏览器已经读取的头像文件内容。 */
export interface AvatarUploadContent {
  readonly body: ArrayBuffer;
  readonly contentType: string;
  readonly filename: string;
  readonly sizeBytes: number;
}

/** 保存签名地址及其到期毫秒时间。 */
interface CachedAccessUrl {
  readonly expiresAt: number;
  readonly url: string;
}

/** 保存一个文件在指定 Session 与清理代次中的签名请求及等待者。 */
interface PendingAccessUrl {
  readonly fileId: Int64String;
  readonly generation: number;
  readonly resolvers: Array<{
    readonly reject: (reason?: unknown) => void;
    readonly resolve: () => void;
  }>;
  readonly scope: string;
  state: 'inFlight' | 'queued';
}

/** 管理文件上传、完成确认和短期读取地址缓存。 */
export class FilesController {
  private readonly accessUrls = new Map<string, CachedAccessUrl>();
  private readonly api: AdminApiClient;
  private flushScheduled = false;
  private generation = 0;
  private readonly pendingAccessUrls = new Map<string, PendingAccessUrl>();
  private readonly sessionScope: () => string;
  private readonly transfers: ObjectTransferPort;

  /** 使用 App API、对象传输端口和 Session 作用域创建文件控制器。 */
  constructor(
    api: AdminApiClient,
    transfers: ObjectTransferPort,
    sessionScope: () => string,
  ) {
    this.api = api;
    this.sessionScope = sessionScope;
    this.transfers = transfers;
  }

  /** 清除全部 Session 的短期读取地址和等待中的请求。 */
  clear(): void {
    this.generation += 1;
    this.accessUrls.clear();
    const error = new AdminError(
      'authentication',
      'Session changed while resolving file access URL',
    );
    this.pendingAccessUrls.forEach((pending) =>
      pending.resolvers.forEach(({ reject }) => reject(error)),
    );
    this.pendingAccessUrls.clear();
  }

  /** 删除指定文件的读取地址缓存，使下一次读取重新申请签名。 */
  invalidate(fileId: Int64String): void {
    this.accessUrls.delete(this.cacheKey(this.sessionScope(), fileId));
  }

  /** 返回一个文件的短期读取地址；没有可用地址时返回空值。 */
  async resolveAccessUrl(
    fileId: Int64String,
    forceRefresh = false,
  ): Promise<null | string> {
    const urls = await this.resolveAccessUrls([fileId], forceRefresh);
    return urls.get(fileId) ?? null;
  }

  /** 批量返回文件读取地址；未到安全刷新时间的地址直接使用缓存。 */
  async resolveAccessUrls(
    fileIds: readonly Int64String[],
    forceRefresh = false,
  ): Promise<ReadonlyMap<Int64String, string>> {
    const unique = [...new Set(fileIds)];
    if (unique.length === 0) return new Map();

    const scope = this.sessionScope();
    const generation = this.generation;
    const now = Date.now();
    const pending = unique.flatMap((fileId) => {
      const cached = this.accessUrls.get(this.cacheKey(scope, fileId));
      if (
        !forceRefresh &&
        cached &&
        cached.expiresAt - ACCESS_URL_SAFETY_MILLISECONDS > now
      ) {
        return [];
      }
      this.accessUrls.delete(this.cacheKey(scope, fileId));
      return [this.enqueueAccessUrl(scope, fileId)];
    });
    await Promise.all(pending);
    if (this.sessionScope() !== scope || this.generation !== generation) {
      throw new AdminError(
        'authentication',
        'Session changed while resolving file access URL',
      );
    }

    return new Map(
      unique.flatMap((fileId) => {
        const cached = this.accessUrls.get(this.cacheKey(scope, fileId));
        return cached ? [[fileId, cached.url] as const] : [];
      }),
    );
  }

  /** 为目标 Platform Account 上传一份头像并返回完成上传的文件标识。 */
  async uploadAvatar(
    accountId: Int64String,
    content: AvatarUploadContent,
  ): Promise<Int64String> {
    const created = await this.api.post<FileUpload>('/files/uploads', {
      targetAccountId: accountId,
      filename: content.filename,
      contentType: content.contentType,
      sizeBytes: content.sizeBytes,
    });
    if (created.data.method !== 'PUT') {
      throw new AdminError('unavailable', 'Unsupported upload method');
    }
    try {
      await this.transfers.transfer({
        method: created.data.method,
        url: created.data.url,
        headers: created.data.headers,
        body: content.body,
      });
    } catch (error) {
      throw mapNetworkError(error);
    }
    await this.api.post<unknown>(
      `/files/uploads/${created.data.fileId}/completion`,
    );
    return created.data.fileId;
  }

  /** 使用 Session 作用域隔离不同登录主体的 URL 缓存。 */
  private cacheKey(scope: string, fileId: Int64String): string {
    return `${scope}:${fileId}`;
  }

  /** 把文件加入当前微任务的批量签名队列，并复用同一文件的等待者。 */
  private enqueueAccessUrl(scope: string, fileId: Int64String): Promise<void> {
    const key = this.cacheKey(scope, fileId);
    const existing = this.pendingAccessUrls.get(key);
    if (existing) {
      return new Promise((resolve, reject) => {
        existing.resolvers.push({ reject, resolve });
      });
    }

    const pending: PendingAccessUrl = {
      fileId,
      generation: this.generation,
      resolvers: [],
      scope,
      state: 'queued',
    };
    this.pendingAccessUrls.set(key, pending);
    if (!this.flushScheduled) {
      this.flushScheduled = true;
      queueMicrotask(() => void this.flushAccessUrls());
    }
    return new Promise((resolve, reject) => {
      pending.resolvers.push({ reject, resolve });
    });
  }

  /** 在微任务边界把同时到达的文件 ID 按后端上限批量换签。 */
  private async flushAccessUrls(): Promise<void> {
    this.flushScheduled = false;
    const entries = [...this.pendingAccessUrls.entries()].filter(
      ([, pending]) => pending.state === 'queued',
    );
    entries.forEach(([, pending]) => {
      pending.state = 'inFlight';
    });
    const activeScope = this.sessionScope();
    const grouped = new Map<string, Array<[string, PendingAccessUrl]>>();
    entries.forEach(([key, pending]) => {
      if (pending.scope !== activeScope) {
        const error = new AdminError(
          'authentication',
          'Session changed while resolving file access URL',
        );
        pending.resolvers.forEach(({ reject }) => reject(error));
        if (this.pendingAccessUrls.get(key) === pending) {
          this.pendingAccessUrls.delete(key);
        }
        return;
      }
      const group = grouped.get(pending.scope) ?? [];
      group.push([key, pending]);
      grouped.set(pending.scope, group);
    });

    const chunks: Array<Array<[string, PendingAccessUrl]>> = [];
    grouped.forEach((group) => {
      for (let index = 0; index < group.length; index += 100) {
        chunks.push(group.slice(index, index + 100));
      }
    });
    if (chunks.length === 0) {
      return;
    }

    await Promise.all(
      chunks.map(async (chunk) => {
        const scope = chunk[0]?.[1].scope;
        const generation = chunk[0]?.[1].generation;
        if (scope === undefined || generation === undefined) return;
        const ids = chunk.map(([, pending]) => pending.fileId);
        try {
          const result = await this.api.post<readonly FileAccessUrl[]>(
            '/files/access-urls',
            { fileIds: ids },
          );
          if (this.sessionScope() !== scope || this.generation !== generation) {
            const error = new AdminError(
              'authentication',
              'Session changed while resolving file access URL',
            );
            chunk.forEach(([key, pending]) => {
              if (this.pendingAccessUrls.get(key) === pending) {
                this.pendingAccessUrls.delete(key);
              }
              pending.resolvers.forEach(({ reject }) => reject(error));
            });
            return;
          }
          result.data.forEach((access) => {
            const expiresAt = Date.parse(access.expiresAt);
            if (access.url && Number.isFinite(expiresAt)) {
              this.accessUrls.set(this.cacheKey(scope, access.fileId), {
                expiresAt,
                url: access.url,
              });
            }
          });
          chunk.forEach(([key, pending]) => {
            if (this.pendingAccessUrls.get(key) === pending) {
              this.pendingAccessUrls.delete(key);
            }
            pending.resolvers.forEach(({ resolve }) => resolve());
          });
        } catch (error) {
          chunk.forEach(([key, pending]) => {
            if (this.pendingAccessUrls.get(key) === pending) {
              this.pendingAccessUrls.delete(key);
            }
            pending.resolvers.forEach(({ reject }) => reject(error));
          });
        }
      }),
    );
  }
}
