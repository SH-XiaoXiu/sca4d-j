# 上传并使用业务文件

以下步骤使用现有的 `im.message.attachment` 场景上传一张图片，并把 fileId 写入 IM 消息。

管理端 DEFAULT Profile 头像提供了可直接参考的完整实现：浏览器裁剪为 512×512 WebP，随后执行签名、DIRECT/PROXY 上传、complete 和业务绑定。源文件只接受 JPEG、PNG、WebP，最大 5 MB；签名策略和最终绑定都会校验约束。

## 前置条件

- 后端已经启动并启用文件功能。
- 当前用户已经登录，并且是目标 IM 会话的成员。
- 已安装 `curl` 和 `jq`。
- 使用 LOCAL 存储时设置 `SCA4DJ_FILE_STORAGE=LOCAL`；使用 MINIO 时确认 public endpoint 可以从当前终端访问。

设置请求变量：

```bash
export API_BASE_URL=http://localhost:8080
export TOKEN='<登录接口返回的 tokenValue>'
export CONVERSATION_ID='<目标会话 ID>'
export FILE_PATH='/absolute/path/to/image.png'
export FILE_SIZE="$(wc -c < "$FILE_PATH" | tr -d ' ')"
```

## 1. 申请上传票据

```bash
curl -sS -X POST "$API_BASE_URL/files/signatures" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"items\": [{
      \"requestId\": \"upload-im-image-1\",
      \"operation\": \"UPLOAD\",
      \"scene\": \"im.message.attachment\",
      \"resourceId\": \"$CONVERSATION_ID\",
      \"originalFilename\": \"image.png\",
      \"contentType\": \"image/png\",
      \"sizeBytes\": $FILE_SIZE
    }]
  }" | tee /tmp/sca4dj-file-signature.json
```

读取返回值：

```bash
export FILE_ID="$(jq -r '.data.items[0].upload.fileId' \
  /tmp/sca4dj-file-signature.json)"
export UPLOAD_MODE="$(jq -r '.data.items[0].upload.uploadMode' \
  /tmp/sca4dj-file-signature.json)"
export UPLOAD_URL="$(jq -r '.data.items[0].upload.url' \
  /tmp/sca4dj-file-signature.json)"
export UPLOAD_GRANT="$(jq -r '.data.items[0].upload.uploadGrant' \
  /tmp/sca4dj-file-signature.json)"
```

返回的 `expiresAt` 是上传地址的过期时间。过期后重新申请，不要重复使用旧地址。

## 2. 上传文件内容

LOCAL 存储返回 PROXY 地址。使用返回的 Method、URL 和 Header 上传：

```bash
curl -sS -X PUT "$API_BASE_URL$UPLOAD_URL" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: image/png' \
  --data-binary "@$FILE_PATH"
```

MINIO 存储通常返回 DIRECT 预签名地址。按响应中的 `method`、`headers` 和 `formFields` 原样发送请求。当前 MinIO 实现使用 PUT 时可执行：

```bash
curl -sS -X PUT "$UPLOAD_URL" \
  -H 'Content-Type: image/png' \
  --data-binary "@$FILE_PATH"
```

## 3. 确认上传完成

```bash
curl -sS -X PUT \
  "$API_BASE_URL/files/uploads/$FILE_ID/completion" \
  -H "Authorization: $TOKEN"
```

确认成功后文件状态从 `PENDING` 变为 `READY`。此时文件还没有被 IM 消息使用。

## 4. 发送图片消息

```bash
curl -sS -X POST "$API_BASE_URL/im/conversations/$CONVERSATION_ID/messages" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"contentType\": \"IMAGE\",
    \"attachmentFileId\": \"$FILE_ID\",
    \"attachmentUploadGrant\": \"$UPLOAD_GRANT\"
  }"
```

`ImServiceImpl` 在消息事务中调用：

```java
fileService.consumeFile(
        attachmentFileId,
        attachmentUploadGrant,
        ImFileSignaturePolicy.MESSAGE_ATTACHMENT,
        conversationId.toString(),
        principal.requirePrincipalId());
```

调用成功后文件状态变为 `IN_USE`，`im_message.attachment_file_id` 保存 fileId。同一个上传 Grant 不能绑定到另一个会话或文件。

## 5. 申请读取地址

先从消息列表取得 `messageId` 和 `attachmentFileId`，再批量申请读取地址：

```bash
export MESSAGE_ID='<图片消息 ID>'

curl -sS -X POST "$API_BASE_URL/files/signatures" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"items\": [{
      \"requestId\": \"read-im-image-1\",
      \"operation\": \"READ\",
      \"scene\": \"im.message.attachment\",
      \"resourceId\": \"$MESSAGE_ID\",
      \"fileId\": \"$FILE_ID\"
    }]
  }"
```

响应中的 `data.items[0].files[0].url` 是短期地址。业务表和前端本地数据只保存 fileId，不保存该 URL。

## 添加新的文件场景

业务模块需要完成以下修改：

1. 定义至少三级的场景常量，例如 `product.cover.image`。
2. 实现 `FileSignaturePolicy.scenes()` 返回该常量。
3. 在 `authorize(...)` 中批量检查 UPLOAD 的修改权限和 READ 的 fileId 对应关系。
4. 在业务 View 中返回 `FileSignatureRefView`，字段为 scene、resourceId 和 fileId。
5. 在业务 Service 中调用 `consumeFile(...)`，把 Grant 与 scene、resourceId、fileId 和 Principal 一起校验。
6. 替换文件后调用 `requestDelete(oldFileId)`。
7. 添加上传、读取、越权、Grant 过期、重复消费和清理测试。

`FileSignaturePolicy.authorize(...)` 每批只会调用一次。实现需要批量查询资源，不要按 item 逐条查询。

## 默认头像参考链路

当前 Subject 使用 `PUT /me/profile` 和 `DELETE /me/profile/avatar` 修改 DEFAULT Profile。替换请求包含 `fileId`、`uploadGrant`、`profileVersion`，清空请求包含 `profileVersion`。

前端公共包的 `createFileApi` 负责两种上传模式：PROXY 复用应用 API Client 和认证信息，DIRECT 使用独立请求且只发送签名响应给出的 Header，不能向对象存储泄漏应用 token。业务页面只保存 `FileSignatureRef`，短期 URL 由 Pinia 文件 Store 批量解析和缓存。

创建 Account 可以先上传默认头像，再把 `avatarFileId` 和 `avatarUploadGrant` 随创建请求一次提交。已有 Subject 修改头像时，UPLOAD 签名的 `resourceId` 为 DEFAULT Principal；当前 Subject 的自助权限由 DEFAULT Profile 文件策略在后端判断。

## 检查结果

- 非会话成员申请上传或读取时返回 403；
- scene、resourceId、fileId 或 Grant 不匹配时请求失败；
- 上传完成但没有写入业务字段的 READY 文件会在 TTL 到期后清理；
- 删除对象存储失败时文件状态为 `DELETE_FAILED`，后台任务会再次尝试；
- 一批读取请求的 SQL 数量不随文件数量增加。
