import type { JsonValue } from '@sca4d/admin';

import type { Preferences } from '@vben/preferences';
import type { DeepPartial } from '@vben/types';

import { readonly, ref, toRaw, watch } from 'vue';

import { preferences, preferencesManager } from '@vben/preferences';

import { AdminError } from '@sca4d/admin';
import { message, Modal } from 'ant-design-vue';

import { $t } from '#/locales';

const PREFERENCE_KEY = 'ADMIN:UI_PREFERENCES';
const SAVE_DELAY_MILLISECONDS = 150;

const pending = ref(false);

let activePrincipalId: null | string = null;
let applying = false;
let cacheNamespace = '';
let dirty = false;
let etag: null | string = null;
let saveTimer: null | ReturnType<typeof setTimeout> = null;
let saving = false;
let started = false;

/** 当前偏好是否正在等待防抖或服务端保存。 */
export const preferenceSyncPending = readonly(pending);

/** 安装一次偏好变化监听，并设置本地 Principal 分区使用的应用命名空间。 */
export function configurePreferenceSync(namespace: string): void {
  cacheNamespace = namespace;
  if (started) return;
  started = true;
  watch(
    preferences,
    () => {
      if (applying || activePrincipalId === null || etag === null) return;
      dirty = true;
      pending.value = true;
      scheduleSave();
    },
    { deep: true },
  );
}

/** 恢复指定 Principal 的本地快照，再读取服务端权威偏好和 ETag。 */
export async function activatePreferenceSync(
  principalId: string,
): Promise<void> {
  if (activePrincipalId === principalId && etag !== null) return;
  cancelSave();
  activePrincipalId = principalId;
  etag = null;
  dirty = false;
  pending.value = false;

  const cached = readCache(principalId);
  await applyPreferences(cached);

  try {
    const controller = await preferencesController();
    const loaded = await controller.require(PREFERENCE_KEY);
    if (activePrincipalId !== principalId) return;
    etag = loaded.etag;
    const value = preferenceObject(loaded.preference.value);
    writeCache(principalId, value);
    await applyPreferences(value);
  } catch {
    if (activePrincipalId === principalId) {
      message.error($t('page.business.preferenceLoadFailed'));
    }
  }
}

/** 停止当前 Principal 同步，并立即恢复匿名初始偏好。 */
export function deactivatePreferenceSync(): void {
  cancelSave();
  activePrincipalId = null;
  etag = null;
  dirty = false;
  pending.value = false;
  void applyPreferences(null);
}

/** 在 150ms 内合并连续界面变化，并保证同一 Principal 最多执行一个保存请求。 */
function scheduleSave(): void {
  if (saving || saveTimer !== null) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    void flush();
  }, SAVE_DELAY_MILLISECONDS);
}

/** 保存当前完整偏好；保存期间出现的新变化会在完成后继续排队。 */
async function flush(): Promise<void> {
  const principalId = activePrincipalId;
  const currentEtag = etag;
  if (saving || !dirty || principalId === null || currentEtag === null) return;

  dirty = false;
  saving = true;
  const local = clonePreferences();
  try {
    const controller = await preferencesController();
    const saved = await controller.replace(PREFERENCE_KEY, local, currentEtag);
    if (activePrincipalId !== principalId) return;
    etag = saved.etag;
    writeCache(principalId, preferenceObject(saved.preference.value));
  } catch (error) {
    if (
      activePrincipalId === principalId &&
      error instanceof AdminError &&
      error.kind === 'versionConflict'
    ) {
      await resolveVersionConflict(principalId, local);
    } else if (activePrincipalId === principalId) {
      message.error($t('page.business.preferenceSaveFailed'));
    }
  } finally {
    saving = false;
    if (activePrincipalId === principalId && dirty) {
      scheduleSave();
    } else {
      pending.value = false;
    }
  }
}

/** 读取最新服务端版本，并让用户选择覆盖服务端或采用服务端值。 */
async function resolveVersionConflict(
  principalId: string,
  local: JsonValue,
): Promise<void> {
  const controller = await preferencesController();
  const current = await controller.require(PREFERENCE_KEY);
  if (activePrincipalId !== principalId) return;
  const choice = await chooseConflictResult();
  if (activePrincipalId !== principalId) return;

  if (choice === 'server') {
    etag = current.etag;
    const server = preferenceObject(current.preference.value);
    writeCache(principalId, server);
    await applyPreferences(server);
    return;
  }

  const saved = await controller.replace(PREFERENCE_KEY, local, current.etag);
  if (activePrincipalId !== principalId) return;
  etag = saved.etag;
  writeCache(principalId, preferenceObject(saved.preference.value));
}

/** 显示 412 冲突选择；确认保留本地，取消采用服务端。 */
function chooseConflictResult(): Promise<'local' | 'server'> {
  return new Promise((resolve) => {
    Modal.confirm({
      cancelText: $t('page.business.preferenceUseServer'),
      content: $t('page.business.preferenceConflictDescription'),
      okText: $t('page.business.preferenceKeepLocal'),
      onCancel: () => resolve('server'),
      onOk: () => resolve('local'),
      title: $t('page.business.preferenceConflictTitle'),
    });
  });
}

/** 恢复初始偏好并合并服务端或 Principal 本地快照。 */
async function applyPreferences(
  value: DeepPartial<Preferences> | null,
): Promise<void> {
  applying = true;
  try {
    await preferencesManager.resetPreferences();
    if (value) preferencesManager.updatePreferences(value);
  } finally {
    applying = false;
  }
}

/** 返回当前完整偏好的独立 JSON 快照。 */
function clonePreferences(): JsonValue {
  return structuredClone(toRaw(preferences)) as unknown as JsonValue;
}

/** 只接受偏好接口约定的 JSON object。 */
function preferenceObject(value: JsonValue): DeepPartial<Preferences> {
  if (value === null || Array.isArray(value) || typeof value !== 'object') {
    throw new Error('UI preference value is not a JSON object');
  }
  return value as DeepPartial<Preferences>;
}

/** 读取指定 Principal 的本地偏好快照。 */
function readCache(principalId: string): DeepPartial<Preferences> | null {
  try {
    const value = localStorage.getItem(cacheKey(principalId));
    return value ? preferenceObject(JSON.parse(value) as JsonValue) : null;
  } catch {
    return null;
  }
}

/** 保存指定 Principal 的本地偏好快照。 */
function writeCache(
  principalId: string,
  value: DeepPartial<Preferences>,
): void {
  try {
    localStorage.setItem(cacheKey(principalId), JSON.stringify(value));
  } catch {
    // 本地缓存不可用不影响服务端偏好读写。
  }
}

/** 返回当前应用命名空间内按 Principal 隔离的缓存键。 */
function cacheKey(principalId: string): string {
  return `${cacheNamespace}:principal:${principalId}:${PREFERENCE_KEY}`;
}

/** 取消尚未执行的防抖保存。 */
function cancelSave(): void {
  if (saveTimer !== null) clearTimeout(saveTimer);
  saveTimer = null;
}

/** 延迟读取业务 Controller，避免认证 Store 与应用端口初始化形成循环依赖。 */
async function preferencesController() {
  const { businessApplication } =
    await import('#/platform/business-application');
  return businessApplication.preferences;
}
