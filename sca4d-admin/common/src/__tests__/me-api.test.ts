import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createMeApi } from "../apis/me";

const get = vi.fn();
const put = vi.fn();
const request = vi.fn();
const client = { get, put, request } as unknown as ApiClient;

describe("me API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("当前账号默认头像替换和清空使用资料版本", async () => {
    put.mockResolvedValue(undefined);
    request.mockResolvedValue(undefined);
    const api = createMeApi(client);

    await api.replaceAvatar("file-1", "grant-2", "profile-v3");
    await api.removeAvatar("profile-v4");

    expect(put).toHaveBeenCalledWith("/me/profile/avatar", {
      fileId: "file-1",
      uploadGrant: "grant-2",
      profileVersion: "profile-v3"
    });
    expect(request).toHaveBeenCalledWith({
      path: "/me/profile/avatar",
      method: "DELETE",
      body: { profileVersion: "profile-v4" }
    });
  });
});
