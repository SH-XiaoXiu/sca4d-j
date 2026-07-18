import type { ApiClient } from "../api-client";
import type { Id, MeSelectionQuery, MeView } from "../types";

/** 创建读取当前用户快照和修改当前头像的请求方法。 */
export function createMeApi(client: ApiClient) {
  return {
    current(query?: MeSelectionQuery): Promise<MeView> {
      return client.get<MeView>("/me", query);
    },
    replaceAvatar(fileId: Id, uploadGrant: string, profileVersion: Id): Promise<void> {
      return client.put("/me/profile/avatar", { fileId, uploadGrant, profileVersion });
    },
    removeAvatar(profileVersion: Id): Promise<void> {
      return client.request({ path: "/me/profile/avatar", method: "DELETE", body: { profileVersion } });
    }
  };
}
