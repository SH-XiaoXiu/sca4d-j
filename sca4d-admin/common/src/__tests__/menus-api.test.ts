import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createSystemMenuApi } from "../apis/menus";

const request = vi.fn();
const get = vi.fn();
const post = vi.fn();
const put = vi.fn();

const client = {
  request,
  get,
  post,
  put
} as unknown as ApiClient;

describe("system menu API", () => {
  beforeEach(() => vi.clearAllMocks());

  it("菜单写入只提交注册路由编码和展示事实", async () => {
    post.mockResolvedValue("menu-1");
    const input = {
      routeCode: "system.menus",
      titles: { "zh-CN": "菜单管理", "en-US": "Menus" },
      icon: "MENUS" as const,
      visible: true
    };

    await createSystemMenuApi(client).create(input, "intent-1");

    expect(post).toHaveBeenCalledWith("/mg/system/menus", {
      ...input,
      idempotencyKey: "intent-1"
    });
    expect(post.mock.calls[0]?.[1]).not.toHaveProperty("path");
    expect(post.mock.calls[0]?.[1]).not.toHaveProperty("component");
    expect(post.mock.calls[0]?.[1]).not.toHaveProperty("permissionCode");
  });

  it("递归删除携带根版本和幂等键", async () => {
    request.mockResolvedValue(undefined);

    await createSystemMenuApi(client).delete("menu-1", "version-2", "intent-3");

    expect(request).toHaveBeenCalledWith({
      path: "/mg/system/menus/menu-1",
      method: "DELETE",
      body: { version: "version-2", idempotencyKey: "intent-3" }
    });
  });

  it("菜单更新只追加当前乐观锁版本", async () => {
    put.mockResolvedValue(undefined);
    const input = {
      titles: { "zh-CN": "系统管理", "en-US": "System" },
      icon: "MENU" as const,
      visible: true
    };

    await createSystemMenuApi(client).update("menu-1", input, "version-4");

    expect(put).toHaveBeenCalledWith("/mg/system/menus/menu-1", {
      ...input,
      version: "version-4"
    });
  });
});
