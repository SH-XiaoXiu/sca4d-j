package com.sca4d.file.view;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 业务响应中的共用文件签名引用。
 *
 * <p>{@code scene} 由返回该引用的业务模块以文件签名策略常量提供，客户端不得自行发明场景编码。
 */
@Data
@Accessors(chain = true)
public class FileSignatureRefView {
    private String scene;
    private String resourceId;
    private Long fileId;
}
