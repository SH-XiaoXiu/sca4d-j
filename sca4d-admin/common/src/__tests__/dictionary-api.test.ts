import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createDictionaryApi } from "../apis/dictionaries";

const request = vi.fn();
const get = vi.fn();
const post = vi.fn();
const put = vi.fn();

const client = {
  request,
  get,
  post,
  put,
  patch: vi.fn(),
  delete: vi.fn()
} as unknown as ApiClient;

describe("dictionary API", () => {
  beforeEach(() => vi.clearAllMocks());

  it("字典选项通过 GET 数组查询参数批量读取", async () => {
    get.mockResolvedValue([]);
    const signal = new AbortController().signal;

    await createDictionaryApi(client, "/mg/system/dictionaries").options(
      ["system.dictionary-kind", "system.dictionary-status"],
      signal
    );

    expect(get).toHaveBeenCalledWith(
      "/mg/system/dictionaries/options",
      { codes: ["system.dictionary-kind", "system.dictionary-status"] },
      signal
    );
    expect(post).not.toHaveBeenCalled();
  });

  it("删除字典项使用 DELETE 请求体传递版本和幂等键", async () => {
    request.mockResolvedValue(undefined);

    await createDictionaryApi(client, "/mg/system/dictionaries").deleteItem(
      "dictionary-1",
      "item-2",
      "version-3",
      "request-4"
    );

    expect(request).toHaveBeenCalledWith({
      path: "/mg/system/dictionaries/dictionary-1/items/item-2",
      method: "DELETE",
      body: { version: "version-3", idempotencyKey: "request-4" }
    });
  });
});
