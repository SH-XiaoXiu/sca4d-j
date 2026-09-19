import { requestClient } from '#/api/request';

export interface InboxMessage {
  readonly createdAt: string;
  readonly html: string;
  readonly id: string;
  readonly languageTag: string;
  readonly notificationType: string;
  readonly readAt: null | string;
  readonly text: string;
  readonly title: string;
  readonly version: string;
}

export interface InboxPage {
  readonly messages: readonly InboxMessage[];
  readonly unreadCount: number;
}

export interface NotificationSettings {
  readonly channels: Readonly<
    Record<string, Readonly<Record<string, boolean>>>
  >;
  readonly channelsVersion: string;
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
  readonly languageVersion: string;
  readonly regionCode: string;
  readonly regionVersion: string;
}

/** 读取当前 Account 的站内信页和未读数量。 */
export async function getInboxApi(beforeId?: string, limit = 20) {
  return requestClient.get<InboxPage>('/me/notifications', {
    params: {
      ...(beforeId ? { beforeId } : {}),
      limit,
    },
  });
}

/** 按当前 Version 把一条站内信标记已读。 */
export async function markInboxReadApi(message: InboxMessage) {
  return requestClient.post<void>(
    `/me/notifications/${encodeURIComponent(message.id)}/read`,
    { version: message.version },
  );
}

/** 把当前 Account 全部站内信标记已读。 */
export async function markAllInboxReadApi() {
  return requestClient.post<void>('/me/notifications/read-all');
}

/** 读取通知框架包装后的 Account 语言、区域和可选渠道设置。 */
export async function getNotificationSettingsApi() {
  return requestClient.get<NotificationSettings>('/me/notification-settings');
}

/** 使用语言设置 Version 替换全局通知语言。 */
export async function replaceNotificationLanguageApi(
  settings: NotificationSettings,
  languageTag: string,
) {
  return requestClient.put<NotificationSettings>(
    '/me/notification-settings/language',
    { value: languageTag },
    { headers: { 'If-Match': `"${settings.languageVersion}"` } },
  );
}

/** 使用区域设置 Version 替换业务区域。 */
export async function replaceNotificationRegionApi(
  settings: NotificationSettings,
  regionCode: string,
) {
  return requestClient.put<NotificationSettings>(
    '/me/notification-settings/region',
    { value: regionCode },
    { headers: { 'If-Match': `"${settings.regionVersion}"` } },
  );
}

/** 使用渠道设置 Version 替换全部可选渠道覆盖值。 */
export async function replaceNotificationChannelsApi(
  settings: NotificationSettings,
  channels: NotificationSettings['channels'],
) {
  return requestClient.put<NotificationSettings>(
    '/me/notification-settings/channels',
    { value: channels },
    { headers: { 'If-Match': `"${settings.channelsVersion}"` } },
  );
}
