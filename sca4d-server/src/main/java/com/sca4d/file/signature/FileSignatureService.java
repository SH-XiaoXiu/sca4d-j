package com.sca4d.file.signature;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 批量校验文件访问请求，并签发读取或上传授权。
 */
public interface FileSignatureService {

    /**
     * 对一批读取或上传意图完成整批业务授权后签发结果。
     *
     * <p>任何一项格式或业务策略判定失败时，整批操作不会创建上传文件或签发访问地址。
     *
     * @param requestedItems 具有固定场景和请求关联标识的签名意图
     * @param currentRequest 当前共用请求态；匿名读取时不包含认证身份
     * @return 与请求顺序一致的文件访问或上传结果
     */
    @NotNull
    FileSignatureResult sign(
            @NotNull List<FileSignatureItem> requestedItems,
            @NotNull CurrentRequest currentRequest);
}
