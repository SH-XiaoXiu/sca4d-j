import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createMailSenderApi } from "../apis/mail-senders";

const get = vi.fn();
const post = vi.fn();
const put = vi.fn();
const request = vi.fn();
const requestResult = vi.fn();
const client = { get, post, put, request, requestResult } as unknown as ApiClient;

describe("mail sender API", () => {
  beforeEach(() => vi.clearAllMocks());

  it("创建请求把幂等键放在请求头", async () => {
    const input = {
      name: "Primary",
      host: "smtp.example.com",
      port: 587,
      security: "STARTTLS" as const,
      username: "alice",
      password: "secret-password",
      fromAddress: "no-reply@example.com"
    };

    await createMailSenderApi(client).create(input, "intent-1");

    expect(requestResult).toHaveBeenCalledWith({
      path: "/mg/system/mail-senders",
      method: "POST",
      body: input,
      headers: { "Idempotency-Key": "intent-1" }
    });
  });

  it("状态和删除使用主资源 If-Match，连接测试使用名词子资源", async () => {
    const api = createMailSenderApi(client);
    await api.changeStatus("sender-1", "ACTIVE", "version-2");
    await api.testConnection("sender-1");
    await api.delete("sender-1", "3");

    expect(requestResult).toHaveBeenNthCalledWith(1, {
      path: "/mg/system/mail-senders/sender-1",
      method: "PATCH",
      body: { status: "ACTIVE" },
      headers: { "If-Match": '"version-2"' }
    });
    expect(post).toHaveBeenCalledWith("/mg/system/mail-senders/sender-1/connection-tests");
    expect(requestResult).toHaveBeenNthCalledWith(2, {
      path: "/mg/system/mail-senders/sender-1",
      method: "DELETE",
      headers: { "If-Match": '"3"' }
    });
  });
});
