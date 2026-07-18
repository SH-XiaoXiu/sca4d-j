import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createMailTemplateApi } from "../apis/mail-templates";

const get = vi.fn();
const post = vi.fn();
const put = vi.fn();
const request = vi.fn();
const requestResult = vi.fn();
const client = { get, post, put, request, requestResult } as unknown as ApiClient;

describe("mail template API", () => {
  beforeEach(() => vi.clearAllMocks());

  it("预览草稿时显式提交模板内容和变量", async () => {
    const input = {
      family: "auth.email-code",
      locale: "zh-CN",
      subjectTemplate: "验证码 {{code}}",
      textTemplate: "验证码是 {{code}}",
      htmlTemplate: "<strong>{{code}}</strong>"
    };

    await createMailTemplateApi(client).preview(input, { code: "123456" });

    expect(post).toHaveBeenCalledWith("/mg/system/mail-template-previews", {
      ...input,
      variables: { code: "123456" }
    });
  });

  it("测试邮件使用名词子资源，状态和删除使用主资源 If-Match", async () => {
    const api = createMailTemplateApi(client);
    await api.sendTest("template-1", "sender-2", "alice@example.com", { code: "123456" });
    await api.changeStatus("template-1", "ACTIVE", "version-3");
    await api.delete("template-1", "4");

    expect(post).toHaveBeenCalledWith("/mg/system/mail-templates/template-1/test-messages", {
      senderId: "sender-2",
      recipient: "alice@example.com",
      variables: { code: "123456" }
    });
    expect(requestResult).toHaveBeenNthCalledWith(1, {
      path: "/mg/system/mail-templates/template-1",
      method: "PATCH",
      body: { status: "ACTIVE" },
      headers: { "If-Match": '"version-3"' }
    });
    expect(requestResult).toHaveBeenNthCalledWith(2, {
      path: "/mg/system/mail-templates/template-1",
      method: "DELETE",
      headers: { "If-Match": '"4"' }
    });
  });
});
