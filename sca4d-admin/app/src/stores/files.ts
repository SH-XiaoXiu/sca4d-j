import type { FileAccessUrl, FileSignatureRef } from "@sca4d/admin-common";
import { defineStore } from "pinia";
import { ref } from "vue";
import { fileApi } from "../api/client";

interface CachedFileUrl {
  url: string;
  expiresAt: string;
}

const REFRESH_AHEAD_MS = 30_000;

function referenceKey(reference: FileSignatureRef): string {
  return `${reference.scene}:${reference.resourceId}:${reference.fileId}`;
}

/** 签发头像上传、发送文件字节并完成上传的 Pinia Store。 */
export const useFileStore = defineStore("files", () => {
  const cached = ref(new Map<string, CachedFileUrl>());

  async function resolve(references: FileSignatureRef[]): Promise<Map<string, string>> {
    const unique = new Map<string, FileSignatureRef>();
    for (const reference of references) unique.set(referenceKey(reference), reference);
    const now = Date.now() + REFRESH_AHEAD_MS;
    const missing = [...unique.entries()]
      .filter(([key]) => {
        const value = cached.value.get(key);
        return !value || new Date(value.expiresAt).getTime() <= now;
      })
      .map(([, reference]) => reference);
    if (missing.length > 0) {
      const accessUrls = await fileApi.read(missing);
      const byFileId = new Map<string, FileAccessUrl>(accessUrls.map((value) => [value.fileId, value]));
      const next = new Map(cached.value);
      for (const reference of missing) {
        const access = byFileId.get(reference.fileId);
        if (access) next.set(referenceKey(reference), { url: access.url, expiresAt: access.expiresAt });
      }
      cached.value = next;
    }
    return new Map(
      [...unique.entries()]
        .map(([key]) => [key, cached.value.get(key)?.url] as const)
        .filter((entry): entry is readonly [string, string] => Boolean(entry[1]))
    );
  }

  async function resolveOne(reference: FileSignatureRef | null): Promise<string | null> {
    if (!reference) return null;
    return (await resolve([reference])).get(referenceKey(reference)) ?? null;
  }

  function invalidate(reference: FileSignatureRef): void {
    const next = new Map(cached.value);
    next.delete(referenceKey(reference));
    cached.value = next;
  }

  function clear(): void {
    cached.value = new Map();
  }

  return { resolve, resolveOne, invalidate, clear, referenceKey };
});
