import axios, { type AxiosRequestConfig } from "axios";
import type { ApiClient, ApiRequest } from "../api-client";
import type {
  FileAccessUrl,
  FileSignatureItem,
  FileSignatureRef,
  FileSignatureResult,
  FileUpload,
  Id
} from "../types";

/** 头像上传完成后返回的文件主键和占用授权。 */
export interface AvatarUpload {
  fileId: Id;
  uploadGrant: string;
}

/** 创建文件签名、上传字节、完成上传和头像上传方法。 */
export function createFileApi(client: ApiClient) {
  const root = "/files";
  return {
    sign(items: FileSignatureItem[]): Promise<FileSignatureResult> {
      return client.post(`${root}/signatures`, { items });
    },
    async signAvatarUpload(file: File, subjectId?: Id): Promise<FileUpload> {
      const item: FileSignatureItem = {
        requestId: globalThis.crypto.randomUUID(),
        operation: "UPLOAD",
        scene: "iam.default-profile.avatar",
        originalFilename: file.name,
        contentType: file.type,
        sizeBytes: file.size
      };
      if (subjectId) item.resourceId = subjectId;
      const result = await client.post<FileSignatureResult>(`${root}/signatures`, { items: [item] });
      const upload = result.items.find((candidate) => candidate.requestId === item.requestId)?.upload;
      if (!upload) throw new Error("Avatar upload signature is missing");
      return upload;
    },
    async read(refs: FileSignatureRef[]): Promise<FileAccessUrl[]> {
      const items: FileSignatureItem[] = refs.map((ref, index) => ({
        requestId: `read-${index}-${ref.fileId}`,
        operation: "READ",
        scene: ref.scene,
        resourceId: ref.resourceId,
        fileId: ref.fileId
      }));
      if (items.length === 0) return [];
      const result = await client.post<FileSignatureResult>(`${root}/signatures`, { items });
      return result.items.flatMap((item) => item.files ?? []);
    },
    async upload(ticket: FileUpload, file: Blob, signal?: AbortSignal): Promise<void> {
      if (ticket.uploadMode === "PROXY") {
        const request: ApiRequest<Blob> = {
          path: ticket.url,
          method: ticket.method,
          body: file,
          headers: ticket.headers
        };
        if (signal) request.signal = signal;
        await client.request<void, Blob>(request);
        return;
      }
      const request: AxiosRequestConfig<Blob> = {
        url: ticket.url,
        method: ticket.method,
        data: file,
        headers: ticket.headers,
        validateStatus: () => true
      };
      if (signal) request.signal = signal;
      const response = await axios.request(request);
      if (response.status < 200 || response.status >= 300) {
        throw new Error(`Direct file upload failed with status ${response.status}`);
      }
    },
    complete(fileId: Id): Promise<void> {
      return client.put(`${root}/uploads/${fileId}/completion`);
    }
  };
}

/** 文件签名和头像上传请求接口。 */
export type FileApi = ReturnType<typeof createFileApi>;
