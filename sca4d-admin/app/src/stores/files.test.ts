import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { FileSignatureRef } from "@sca4d/admin-common";

const mocks = vi.hoisted(() => ({ read: vi.fn() }));

vi.mock("../api/client", () => ({ fileApi: { read: mocks.read } }));

import { useFileStore } from "./files";

const reference: FileSignatureRef = {
  scene: "iam.default-profile.avatar",
  resourceId: "subject-1",
  fileId: "file-1"
};

describe("file store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.read.mockReset();
    vi.useRealTimers();
  });

  it("相同引用只签名一次并复用未过期 URL", async () => {
    mocks.read.mockResolvedValue([{ fileId: "file-1", url: "/signed-1", expiresAt: "2099-01-01T00:00:00Z" }]);
    const store = useFileStore();

    await expect(store.resolveOne(reference)).resolves.toBe("/signed-1");
    await expect(store.resolveOne(reference)).resolves.toBe("/signed-1");

    expect(mocks.read).toHaveBeenCalledTimes(1);
    expect(mocks.read).toHaveBeenCalledWith([reference]);
  });

  it("提前三十秒刷新即将过期的 URL", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-07-14T10:00:00Z"));
    mocks.read
      .mockResolvedValueOnce([{ fileId: "file-1", url: "/signed-1", expiresAt: "2026-07-14T10:01:00Z" }])
      .mockResolvedValueOnce([{ fileId: "file-1", url: "/signed-2", expiresAt: "2026-07-14T10:10:00Z" }]);
    const store = useFileStore();

    await expect(store.resolveOne(reference)).resolves.toBe("/signed-1");
    vi.setSystemTime(new Date("2026-07-14T10:00:31Z"));
    await expect(store.resolveOne(reference)).resolves.toBe("/signed-2");

    expect(mocks.read).toHaveBeenCalledTimes(2);
  });

  it("失效指定引用后重新签名且不影响空引用", async () => {
    mocks.read
      .mockResolvedValueOnce([{ fileId: "file-1", url: "/signed-1", expiresAt: "2099-01-01T00:00:00Z" }])
      .mockResolvedValueOnce([{ fileId: "file-1", url: "/signed-2", expiresAt: "2099-01-01T00:00:00Z" }]);
    const store = useFileStore();

    await store.resolveOne(reference);
    store.invalidate(reference);

    await expect(store.resolveOne(reference)).resolves.toBe("/signed-2");
    await expect(store.resolveOne(null)).resolves.toBeNull();
    expect(mocks.read).toHaveBeenCalledTimes(2);
  });
});
