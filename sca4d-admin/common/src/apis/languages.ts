import type { ApiClient } from "../api-client";
import type { LanguageView } from "../types";

/** 创建读取后端支持语言目录的请求方法。 */
export function createLanguageApi(client: ApiClient) {
  return {
    languages(signal?: AbortSignal): Promise<LanguageView[]> {
      return client.get("/system/languages", undefined, signal);
    }
  };
}
