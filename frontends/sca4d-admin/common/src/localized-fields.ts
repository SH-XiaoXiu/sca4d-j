import type { LocaleCatalog, LocalizedFields } from './types';

/** 冻结应用 Locale 目录及其支持列表。 */
export function freezeLocaleCatalog(value: LocaleCatalog): LocaleCatalog {
  return Object.freeze({
    defaultLocale: value.defaultLocale,
    supportedLocales: Object.freeze([...value.supportedLocales]),
  });
}

/** 深度冻结按 Locale 和字段名保存的多语言文本。 */
export function freezeLocalizedFields(value: LocalizedFields): LocalizedFields {
  return Object.freeze(
    Object.fromEntries(
      Object.entries(value).map(([locale, fields]) => [
        locale,
        Object.freeze({ ...fields }),
      ]),
    ),
  );
}
