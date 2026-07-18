package com.sca4d.file.signature;

import lombok.Data;

/**
 * 上传授权声明。
 *
 * <p>{@code scene} 原样保存业务模块注册的文件签名场景编码，用于消费授权时绑定同一业务含义。
 */
@Data
public class FileUploadGrantClaims {
    private String grantType;
    private Long fileId;
    private Long principalId;
    private String scene;
    private String resourceId;
    private long expiresAtEpochSecond;
}
