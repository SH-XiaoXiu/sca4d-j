import axios from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createFileApi } from "../apis/files";
import type { FileUpload } from "../types";

const request = vi.fn();
const post = vi.fn();
const put = vi.fn();
const client = { request, post, put } as unknown as ApiClient;

function uploadTicket(uploadMode: "DIRECT" | "PROXY"): FileUpload {
  return {
    fileId: "file-1",
    uploadMode,
    method: "PUT",
    url: uploadMode === "DIRECT" ? "https://storage.example/avatar" : "/files/uploads/file-1/content",
    headers: { "Content-Type": "image/webp" },
    formFields: {},
    expiresAt: "2026-07-14T12:00:00Z",
    uploadGrant: "grant-1"
  };
}

describe("file API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("默认头像上传签名携带场景、文件约束和目标 Subject", async () => {
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000001");
    post.mockResolvedValue({
      items: [{ requestId: "00000000-0000-4000-8000-000000000001", operation: "UPLOAD", upload: uploadTicket("PROXY") }]
    });
    const file = new File([new Uint8Array(128)], "avatar.webp", { type: "image/webp" });

    await expect(createFileApi(client).signAvatarUpload(file, "subject-2")).resolves.toMatchObject({ fileId: "file-1" });

    expect(post).toHaveBeenCalledWith("/files/signatures", {
      items: [{
        requestId: "00000000-0000-4000-8000-000000000001",
        operation: "UPLOAD",
        scene: "iam.default-profile.avatar",
        resourceId: "subject-2",
        originalFilename: "avatar.webp",
        contentType: "image/webp",
        sizeBytes: 128
      }]
    });
  });

  it("PROXY 上传复用认证客户端并保留签名头", async () => {
    request.mockResolvedValue(undefined);
    const file = new Blob(["avatar"], { type: "image/webp" });

    await createFileApi(client).upload(uploadTicket("PROXY"), file);

    expect(request).toHaveBeenCalledWith({
      path: "/files/uploads/file-1/content",
      method: "PUT",
      body: file,
      headers: { "Content-Type": "image/webp" }
    });
  });

  it("DIRECT 上传使用独立请求且不会注入应用认证头", async () => {
    const axiosRequest = vi.spyOn(axios, "request").mockResolvedValue({ status: 200 } as never);
    const file = new Blob(["avatar"], { type: "image/webp" });

    await createFileApi(client).upload(uploadTicket("DIRECT"), file);

    expect(axiosRequest).toHaveBeenCalledWith(expect.objectContaining({
      url: "https://storage.example/avatar",
      method: "PUT",
      data: file,
      headers: { "Content-Type": "image/webp" }
    }));
    expect(request).not.toHaveBeenCalled();
    expect(axiosRequest.mock.calls[0]?.[0]?.headers).not.toHaveProperty("Authorization");
  });

  it("批量读取和上传完成使用文件资源路由", async () => {
    post.mockResolvedValueOnce({
      items: [{ requestId: "read-0-file-1", operation: "READ", files: [{ fileId: "file-1", url: "/signed", expiresAt: "2026-07-14T12:00:00Z" }] }]
    });
    put.mockResolvedValueOnce(undefined);
    const api = createFileApi(client);

    await expect(api.read([{ scene: "iam.default-profile.avatar", resourceId: "subject-1", fileId: "file-1" }]))
      .resolves.toEqual([{ fileId: "file-1", url: "/signed", expiresAt: "2026-07-14T12:00:00Z" }]);
    await api.complete("file-1");

    expect(post).toHaveBeenNthCalledWith(1, "/files/signatures", {
      items: [{ requestId: "read-0-file-1", operation: "READ", scene: "iam.default-profile.avatar", resourceId: "subject-1", fileId: "file-1" }]
    });
    expect(put).toHaveBeenCalledWith("/files/uploads/file-1/completion");
  });
});
