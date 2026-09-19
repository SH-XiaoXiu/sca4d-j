/** 后端在 Session 变化后发送，管理端收到后重新读取当前 Session。 */
export const AUTHENTICATION_SESSION_CHANGED_EVENT =
  'authentication.session.changed';

/** 后端在站内信新增或已读状态变化后发送，客户端收到后重新读取收件箱。 */
export const NOTIFICATION_INBOX_CHANGED_EVENT = 'notification.inbox.changed';
