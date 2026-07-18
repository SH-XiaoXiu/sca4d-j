package com.sca4d.file.signature;

import lombok.Data;

/** 本地文件读取授权声明。 */
@Data
public class FileReadGrantClaims {
    private String grantType;
    private Long fileId;
    private long expiresAtEpochSecond;
}
