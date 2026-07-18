import type { ApiClient, ApiResult } from "../api-client";
import type {
  Id,
  MailSenderStatus,
  MailSenderView,
  MailSenderWriteInput
} from "../types";

/** 创建 SMTP 邮件发送配置的查询、写入、连接测试和状态请求方法。 */
export function createMailSenderApi(client: ApiClient) {
  const root = "/mg/system/mail-senders";
  return {
    senders(): Promise<MailSenderView[]> {
      return client.get(root);
    },
    detail(senderId: Id): Promise<ApiResult<MailSenderView>> {
      return client.requestResult({ path: `${root}/${senderId}` });
    },
    create(input: MailSenderWriteInput, idempotencyKey: string): Promise<ApiResult<MailSenderView>> {
      return client.requestResult({
        path: root,
        method: "POST",
        body: input,
        headers: { "Idempotency-Key": idempotencyKey }
      });
    },
    update(
      senderId: Id,
      input: MailSenderWriteInput & { clearPassword: boolean },
      version: Id
    ): Promise<ApiResult<MailSenderView>> {
      return client.requestResult({
        path: `${root}/${senderId}`,
        method: "PUT",
        body: input,
        headers: { "If-Match": `"${version}"` }
      });
    },
    changeStatus(senderId: Id, status: MailSenderStatus, version: Id): Promise<ApiResult<MailSenderView>> {
      return client.requestResult({
        path: `${root}/${senderId}`,
        method: "PATCH",
        body: { status },
        headers: { "If-Match": `"${version}"` }
      });
    },
    testConnection(senderId: Id): Promise<void> {
      return client.post(`${root}/${senderId}/connection-tests`);
    },
    delete(senderId: Id, version: Id): Promise<ApiResult<void>> {
      return client.requestResult({
        path: `${root}/${senderId}`,
        method: "DELETE",
        headers: { "If-Match": `"${version}"` }
      });
    }
  };
}
