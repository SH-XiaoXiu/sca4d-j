import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type { Int64String } from './types';

import { ObservableController } from './controller';
import { AdminError, resourcePhaseForError } from './errors';

/** 服务端已经渲染并保存的站内信；id 和 version 保持十进制字符串。 */
export interface InboxMessage {
  readonly createdAt: string;
  readonly html: string;
  readonly id: Int64String;
  readonly languageTag: string;
  readonly notificationType: string;
  readonly readAt: null | string;
  readonly text: string;
  readonly title: string;
  readonly version: Int64String;
}

/** 服务端游标分页结果及整个 Account 的未读数量。 */
export interface InboxPage {
  readonly messages: readonly InboxMessage[];
  readonly unreadCount: number;
}

/** 一个独立站内信视图的只读状态。 */
export interface InboxState {
  readonly beforeId: Int64String | undefined;
  readonly error: AdminError | null;
  readonly loadingMore: boolean;
  readonly messages: readonly InboxMessage[];
  readonly phase: ResourcePhase;
  readonly unreadCount: number;
}

/** 读取站内信游标页并按 Version 标记已读；摘要和完整列表分别创建实例。 */
export class InboxController extends ObservableController<InboxState> {
  constructor(
    private readonly api: AdminApiClient,
    private readonly limit = 20,
  ) {
    super({
      beforeId: undefined,
      error: null,
      loadingMore: false,
      messages: Object.freeze([]),
      phase: 'idle',
      unreadCount: 0,
    });
    if (!Number.isInteger(limit) || limit < 1 || limit > 100)
      throw new RangeError('Inbox limit must be between 1 and 100');
  }

  /** 从第一页替换结果，刷新使此前的追加请求失效。 */
  async load(): Promise<void> {
    const read = this.beginRead('page');
    this.publish({
      ...this.state,
      error: null,
      loadingMore: false,
      phase: 'loading',
    });
    try {
      const result = await this.api.get<InboxPage>('/me/notifications', {
        limit: this.limit,
      });
      if (!this.isCurrentRead('page', read)) return;
      this.accept(result.data, false);
    } catch (error) {
      if (!this.isCurrentRead('page', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 追加一个更早的游标页，重复点击不会产生第二次相同请求。 */
  async loadMore(): Promise<void> {
    const cursor = this.state.beforeId;
    if (!cursor || this.state.loadingMore || this.state.phase === 'loading')
      return;
    const read = this.beginRead('page');
    this.publish({ ...this.state, error: null, loadingMore: true });
    try {
      const result = await this.api.get<InboxPage>('/me/notifications', {
        beforeId: cursor,
        limit: this.limit,
      });
      if (!this.isCurrentRead('page', read)) return;
      this.accept(result.data, true);
    } catch (error) {
      if (!this.isCurrentRead('page', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 标记 Account 的所有消息已读，再读取真实数量。 */
  async markAllRead(): Promise<void> {
    await this.api.post('/me/notifications/read-all');
    await this.load();
  }

  /** 使用用户实际读取的消息版本标记已读，再刷新当前视图。 */
  async markRead(item: InboxMessage): Promise<void> {
    if (item.readAt) return;
    await this.api.post(
      `/me/notifications/${encodeURIComponent(item.id)}/read`,
      { version: item.version },
    );
    await this.load();
  }

  /** 保存当前页的完整快照；追加时按稳定 ID 去重并沿用服务端顺序。 */
  private accept(page: InboxPage, append: boolean): void {
    const items = append
      ? [...this.state.messages, ...page.messages]
      : [...page.messages];
    const messages = Object.freeze([
      ...new Map(
        items.map((item) => [item.id, Object.freeze({ ...item })]),
      ).values(),
    ]);
    this.publish({
      beforeId:
        page.messages.length === this.limit
          ? page.messages.at(-1)?.id
          : undefined,
      error: null,
      loadingMore: false,
      messages,
      phase: messages.length > 0 ? 'ready' : 'empty',
      unreadCount: page.unreadCount,
    });
  }

  /** 保留可读消息和真实未读数量，失败不伪装成空列表。 */
  private fail(error: unknown): void {
    const value =
      error instanceof AdminError
        ? error
        : new AdminError('unknown', 'Request failed');
    this.publish({
      ...this.state,
      error: value,
      loadingMore: false,
      phase: resourcePhaseForError(value),
    });
  }
}
