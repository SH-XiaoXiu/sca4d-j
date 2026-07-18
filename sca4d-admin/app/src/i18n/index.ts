import dayjs from "dayjs";
import "dayjs/locale/en";
import "dayjs/locale/zh-cn";
import { createI18n } from "vue-i18n";
import { enUSMessages } from "./messages/en-US";
import { zhCNMessages } from "./messages/zh-CN";

/** 管理端当前支持的界面语言。 */
export type ApplicationLocale = "zh-CN" | "en-US";

/** 没有保存语言或语言无效时使用的界面语言。 */
export const DEFAULT_LOCALE: ApplicationLocale = "zh-CN";
/** 管理端已打包消息资源的语言列表。 */
export const SUPPORTED_LOCALES: readonly ApplicationLocale[] = ["zh-CN", "en-US"];
/** 浏览器保存界面语言使用的 localStorage 键。 */
export const LOCALE_STORAGE_KEY = "sca4d-admin.locale";

function supportedLocale(value: string | null | undefined): value is ApplicationLocale {
  return SUPPORTED_LOCALES.includes(value as ApplicationLocale);
}

function initialLocale(): ApplicationLocale {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
  if (supportedLocale(stored)) return stored;
  return navigator.language.toLowerCase().startsWith("en") ? "en-US" : DEFAULT_LOCALE;
}

/** Vue 应用使用的共享 vue-i18n 实例。 */
export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  missingWarn: import.meta.env.DEV,
  fallbackWarn: import.meta.env.DEV,
  messages: {
    "zh-CN": zhCNMessages,
    "en-US": enUSMessages
  }
});

/** 返回当前 vue-i18n 界面语言。 */
export function getApplicationLocale(): ApplicationLocale {
  return i18n.global.locale.value;
}

/** 使用当前界面语言解析消息键。 */
export function translateApplicationMessage(messageKey: string): string {
  return i18n.global.t(messageKey);
}

/** 切换 vue-i18n 和 dayjs 语言，并更新 html lang。 */
export function applyApplicationLocale(locale: ApplicationLocale): void {
  i18n.global.locale.value = locale;
  dayjs.locale(locale === "zh-CN" ? "zh-cn" : "en");
  document.documentElement.lang = locale;
}

/** 判断字符串是否为管理端已打包的界面语言。 */
export function isApplicationLocale(value: string | null): value is ApplicationLocale {
  return supportedLocale(value);
}
