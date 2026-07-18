import type { ApiClient } from "../api-client";
import type { ErrorCodeCatalogView, PermissionCatalogView } from "../types";

/** 创建权限目录和错误码目录请求方法。 */
export function createSystemCatalogApi(client: ApiClient) {
  return {
    permissions(): Promise<PermissionCatalogView> {
      return client.get<PermissionCatalogView>("/system/permissions");
    },
    errorCodes(): Promise<ErrorCodeCatalogView> {
      return client.get<ErrorCodeCatalogView>("/system/error-codes");
    }
  };
}
