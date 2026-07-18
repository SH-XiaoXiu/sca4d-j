import type { ApiClient, ApiResult } from "../api-client";
import type {
  Id,
  MailTemplateStatus,
  MailTemplateVariables,
  MailTemplateView,
  MailTemplateWriteInput,
  RenderedMailView
} from "../types";

/** 创建邮件模板管理、预览和测试发送请求方法。 */
export function createMailTemplateApi(client: ApiClient) {
  const root = "/mg/system/mail-templates";
  return {
    templates(): Promise<MailTemplateView[]> {
      return client.get(root);
    },
    detail(templateId: Id): Promise<ApiResult<MailTemplateView>> {
      return client.requestResult({ path: `${root}/${templateId}` });
    },
    create(input: MailTemplateWriteInput, idempotencyKey: string): Promise<ApiResult<MailTemplateView>> {
      return client.requestResult({
        path: root,
        method: "POST",
        body: input,
        headers: { "Idempotency-Key": idempotencyKey }
      });
    },
    update(templateId: Id, input: MailTemplateWriteInput, version: Id): Promise<ApiResult<MailTemplateView>> {
      return client.requestResult({
        path: `${root}/${templateId}`,
        method: "PUT",
        body: input,
        headers: { "If-Match": `"${version}"` }
      });
    },
    changeStatus(templateId: Id, status: MailTemplateStatus, version: Id): Promise<ApiResult<MailTemplateView>> {
      return client.requestResult({
        path: `${root}/${templateId}`,
        method: "PATCH",
        body: { status },
        headers: { "If-Match": `"${version}"` }
      });
    },
    preview(input: MailTemplateWriteInput, variables: MailTemplateVariables): Promise<RenderedMailView> {
      return client.post("/mg/system/mail-template-previews", { ...input, variables });
    },
    sendTest(
      templateId: Id,
      senderId: Id,
      recipient: string,
      variables: MailTemplateVariables
    ): Promise<void> {
      return client.post(`${root}/${templateId}/test-messages`, { senderId, recipient, variables });
    },
    delete(templateId: Id, version: Id): Promise<ApiResult<void>> {
      return client.requestResult({
        path: `${root}/${templateId}`,
        method: "DELETE",
        headers: { "If-Match": `"${version}"` }
      });
    }
  };
}
