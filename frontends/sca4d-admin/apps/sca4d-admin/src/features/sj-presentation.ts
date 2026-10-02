import type { AdminLanguage } from '@sca4d/admin';

import type { SjTranslationKey } from '#/locales';

import { computed } from 'vue';

import { preferences } from '@vben/preferences';

import { AdminError } from '@sca4d/admin';

import { $t } from '#/locales';
/** 使用同一 Vben Locale 展示业务文案、浏览器时区时间及安全错误。 */
export function useSjPresentation() {
  const language = computed(() => preferences.app.locale as AdminLanguage);
  /** 从应用 Locale 中读取业务文案。 */
  const t = (
    key: SjTranslationKey,
    values?: Readonly<Record<string, number | string>>,
  ) => (values ? $t(`sj.${key}`, values) : $t(`sj.${key}`));
  /** 按浏览器时区显示合法时间，缺失或非法值保留占位。 */
  const dateTime = (value: null | string | undefined) => {
    const time = value ? new Date(value) : null;
    return time && Number.isFinite(time.getTime())
      ? new Intl.DateTimeFormat(language.value, {
          dateStyle: 'medium',
          timeStyle: 'short',
        }).format(time)
      : '-';
  };
  /** 未知异常不向用户泄露技术细节，稳定错误保留实际恢复信息。 */
  const errorText = (error: unknown) => {
    if (!(error instanceof AdminError)) return t('unknownError');
    if (error.kind === 'refresh') return t('saveRefreshFailed');
    if (error.kind === 'versionConflict' || error.kind === 'precondition')
      return t('versionConflictDetail');
    if (error.kind === 'network' || error.kind === 'unknown')
      return t('unknownError');
    return error.message || t('unknownError');
  };
  return { language, t, dateTime, errorText };
}
