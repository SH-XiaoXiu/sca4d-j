import { describe, expect, it, vi } from "vitest";
import axios, { AxiosHeaders, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { ApiError } from "../api-error";
import { createApiClient } from "../api-client";

function response(
  config: AxiosRequestConfig,
  body: unknown,
  status = 200,
  headers: Record<string, string> = {}
): AxiosResponse {
  return {
    data: body,
    status,
    statusText: String(status),
    headers: new AxiosHeaders(headers),
    config: config as AxiosResponse["config"]
  };
}

describe("createApiClient", () => {
  it("注入动态 token 和请求语言并解包统一响应", async () => {
    const requestAxios = axios.create({
      adapter: async (config) => {
        expect(AxiosHeaders.from(config.headers).get("Authorization")).toBe("token-value");
        expect(AxiosHeaders.from(config.headers).get("Accept-Language")).toBe("en-US");
        return response(config, {
          code: 0,
          messageKey: null,
          messageArgs: [],
          message: null,
          data: { id: "9007199254740993" },
          traceId: "t1",
          timestamp: "1"
        });
      }
    });
    const client = createApiClient({
      baseUrl: "/api",
      token: () => ({ tokenName: "Authorization", tokenValue: "token-value" }),
      locale: () => "en-US",
      axios: requestAxios
    });

    await expect(client.get<{ id: string }>("/me")).resolves.toEqual({ id: "9007199254740993" });
  });

  it("保留业务错误、traceId、重试时间和客户端状态", async () => {
    const onClientState = vi.fn();
    const client = createApiClient({
      baseUrl: "/api",
      onClientState,
      axios: axios.create({
        adapter: async (config) =>
          response(
            config,
            {
              code: 1008,
              messageKey: "error.rate-limit.exceeded",
              messageArgs: [],
              message: "请求过于频繁",
              data: null,
              traceId: "trace-rate",
              timestamp: "1"
            },
            429,
            {
              "Retry-After": "3",
              "X-Client-State-Stale": "true",
              "X-Client-State-Refresh": "me,permissions"
            }
          )
      })
    });

    const error = await client.get("/limited").catch((caught) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      code: 1008,
      messageKey: "error.rate-limit.exceeded",
      messageArgs: [],
      httpStatus: 429,
      traceId: "trace-rate",
      retryAfterSeconds: 3
    });
    expect(onClientState).toHaveBeenCalledWith(
      expect.objectContaining({ stale: true, refreshTargets: ["me", "permissions"] })
    );
  });

  it("401 只触发会话失效回调一次", async () => {
    const onUnauthorized = vi.fn();
    const client = createApiClient({
      baseUrl: "/api",
      onUnauthorized,
      axios: axios.create({
        adapter: async (config) =>
          response(
            config,
            {
              code: 1001,
              messageKey: "error.authentication.unauthenticated",
              messageArgs: [],
              message: "登录已失效",
              data: null,
              traceId: "trace-auth",
              timestamp: "1"
            },
            401
          )
      })
    });

    await expect(client.get("/me")).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("认证 Proof 返回 401 时保留已有登录会话", async () => {
    const onUnauthorized = vi.fn();
    const client = createApiClient({
      baseUrl: "/api",
      onUnauthorized,
      axios: axios.create({
        adapter: async (config) =>
          response(
            config,
            {
              code: 2007,
              messageKey: "error.authentication.credential-rejected",
              messageArgs: [],
              message: "认证信息不正确",
              data: null,
              traceId: "trace-proof",
              timestamp: "1"
            },
            401
          )
      })
    });

    await expect(
      client.request({
        path: "/auth/flows/flow-1/password/proofs",
        method: "POST",
        body: { password: "invalid" },
        preserveSessionOnUnauthorized: true
      })
    ).rejects.toMatchObject({
      code: 2007,
      messageKey: "error.authentication.credential-rejected",
      httpStatus: 401,
      traceId: "trace-proof"
    });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("序列化分页、数组和日期查询参数", async () => {
    const requestAxios = axios.create({
      paramsSerializer: (parameters) => new URLSearchParams(parameters as Record<string, string>).toString(),
      adapter: async (config) => {
        expect(config.params).toEqual(expect.objectContaining({ page: 2, status: ["ACTIVE", "DISABLED"] }));
        return response(config, {
          code: 0,
          messageKey: null,
          messageArgs: [],
          message: null,
          data: [],
          traceId: null,
          timestamp: "1"
        });
      }
    });
    const client = createApiClient({ baseUrl: "/api", axios: requestAxios });

    await client.get("/items", {
      page: 2,
      status: ["ACTIVE", "DISABLED"],
      createdAt: new Date("2026-07-10T00:00:00Z")
    });
  });

  it("把 204 无响应体识别为成功命令", async () => {
    const client = createApiClient({
      baseUrl: "/api",
      axios: axios.create({ adapter: async (config) => response(config, "", 204) })
    });

    await expect(client.delete<void>("/items/1")).resolves.toBeUndefined();
  });

  it("保留成功响应的状态、Location 和 ETag", async () => {
    const client = createApiClient({
      baseUrl: "/api",
      axios: axios.create({
        adapter: async (config) => response(config, {
          code: 0,
          messageKey: null,
          messageArgs: [],
          message: null,
          data: { id: "42" },
          traceId: null,
          timestamp: "1"
        }, 201, { Location: "/items/42", ETag: '"7"' })
      })
    });

    await expect(client.requestResult<{ id: string }>({ path: "/items", method: "POST" })).resolves.toEqual({
      data: { id: "42" },
      httpStatus: 201,
      location: "/items/42",
      etag: '"7"'
    });
  });

  it("读取二进制资源并保留认证、语言和 MIME 类型", async () => {
    const bytes = new Uint8Array([137, 80, 78, 71]);
    const client = createApiClient({
      baseUrl: "/api",
      token: () => ({ tokenName: "Authorization", tokenValue: "token-value" }),
      locale: () => "zh-CN",
      axios: axios.create({
        adapter: async (config) => {
          const headers = AxiosHeaders.from(config.headers);
          expect(headers.get("Accept")).toBe("application/octet-stream, image/*");
          expect(headers.get("Authorization")).toBe("token-value");
          expect(headers.get("Accept-Language")).toBe("zh-CN");
          expect(config.responseType).toBe("arraybuffer");
          return response(config, bytes.buffer, 200, { "Content-Type": "image/png" });
        }
      })
    });

    const blob = await client.getBlob("/me/accounts/20/totp/enrollments/enrollment-1/qr-code");

    expect(blob.type).toBe("image/png");
    expect(Array.from(new Uint8Array(await blob.arrayBuffer()))).toEqual(Array.from(bytes));
  });

  it("二进制资源返回 401 JSON 时触发会话失效回调", async () => {
    const onUnauthorized = vi.fn();
    const payload = new TextEncoder().encode(JSON.stringify({
      code: 1001,
      messageKey: "error.authentication.unauthenticated",
      messageArgs: [],
      message: "登录已失效",
      data: null,
      traceId: "trace-blob-auth",
      timestamp: "1"
    }));
    const client = createApiClient({
      baseUrl: "/api",
      onUnauthorized,
      axios: axios.create({
        adapter: async (config) => response(config, payload.buffer, 401, { "Content-Type": "application/json" })
      })
    });

    const error = await client.getBlob("/protected.png").catch((caught) => caught);

    expect(error).toMatchObject({
      code: 1001,
      messageKey: "error.authentication.unauthenticated",
      httpStatus: 401,
      traceId: "trace-blob-auth"
    });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("二进制资源返回错误 JSON 时保留业务错误字段", async () => {
    const payload = new TextEncoder().encode(JSON.stringify({
      code: 2023,
      messageKey: "error.authentication.totp-enrollment-unavailable",
      messageArgs: ["enrollment-1"],
      message: "身份验证器设置已失效",
      data: { enrollmentId: "enrollment-1" },
      traceId: "trace-blob-error",
      timestamp: "1"
    }));
    const client = createApiClient({
      baseUrl: "/api",
      axios: axios.create({
        adapter: async (config) => response(config, payload.buffer, 409, {
          "Content-Type": "application/json",
          "Retry-After": "2"
        })
      })
    });

    await expect(client.getBlob("/expired.png")).rejects.toMatchObject({
      code: 2023,
      messageKey: "error.authentication.totp-enrollment-unavailable",
      messageArgs: ["enrollment-1"],
      httpStatus: 409,
      traceId: "trace-blob-error",
      retryAfterSeconds: 2,
      data: { enrollmentId: "enrollment-1" }
    });
  });
});
