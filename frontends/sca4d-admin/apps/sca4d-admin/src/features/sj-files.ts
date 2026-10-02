import type { Int64String } from '@sca4d/admin';

import { onScopeDispose, ref, watch } from 'vue';

import { browserPasskeyClient } from '#/auth/passkey';
import { businessApplication } from '#/platform/business-application';
/** 读取浏览器文件字节后按原有签名上传流程申请并完成头像上传。 */
export async function uploadSjAvatar(accountId: Int64String, file: File) {
  return businessApplication.files.uploadAvatar(accountId, {
    filename: file.name,
    contentType: file.type,
    sizeBytes: file.size,
    body: await file.arrayBuffer(),
  });
}
/** 为当前账号删除流程提交浏览器完成的 Passkey assertion。 */
export async function submitSjAccountDeletionPasskey() {
  return businessApplication.accounts.submitDeletionPasskey(
    await browserPasskeyClient.getAssertion(
      await businessApplication.accounts.createDeletionPasskeyOptions(),
    ),
  );
}
/** 文件标识改变或组件离开时丢弃旧地址，失败后最多强制换签一次。 */
export function useSjFileAccessUrl(
  fileId: () => Int64String | null | undefined,
) {
  const url = ref<string>();
  let generation = 0;
  let retried: Int64String | null = null;
  onScopeDispose(() => {
    generation++;
  });
  /** 只发布属于当前文件和当前请求的签名地址。 */
  async function load(force = false) {
    const id = fileId();
    const version = ++generation;
    url.value = undefined;
    if (!id) return;
    try {
      const result = await businessApplication.files.resolveAccessUrls(
        [id],
        force,
      );
      if (version === generation && id === fileId()) url.value = result.get(id);
    } catch {
      if (version === generation) url.value = undefined;
    }
  }
  watch(
    fileId,
    () => {
      retried = null;
      void load();
    },
    { immediate: true },
  );
  /** 只为同一文件执行一次图片失败换签。 */
  function retry() {
    const id = fileId();
    if (!id || retried === id) {
      generation++;
      url.value = undefined;
      return;
    }
    retried = id;
    void load(true);
  }
  return { url, retry };
}
