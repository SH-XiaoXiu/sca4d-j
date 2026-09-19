package com.sca4d.app.server.platform.request;

/** 接收需要绑定为 Platform 用户头像的文件标识。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class ReplaceAvatarRequest {

    private long fileId;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public ReplaceAvatarRequest() {
    }

    /**
     * 返回已完成上传的头像文件标识。
     *
     * @return 已完成上传的头像文件标识
     */
    public long getFileId() {
        return fileId;
    }

    /**
     * 设置已完成上传的头像文件标识。
     *
     * @param fileId 已完成上传的头像文件标识
     */
    public void setFileId(long fileId) {
        this.fileId = fileId;
    }
}
