package com.sca4d.file.signature;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * 判断业务场景是否允许读取已有文件或上传新文件。
 *
 * <p>场景编码由各业务模块以常量声明并通过实现注册，公共文件模块只校验格式和唯一性，不维护固定枚举。
 */
public interface FileSignaturePolicy {

    /**
     * 当前业务实现支持的固定场景编码。
     *
     * @return 当前业务模块以常量定义的场景编码集合
     */
    @NotNull
    Set<String> scenes();

    /**
     * 批量判定当前请求是否允许签名。
     *
     * <p>FileSignatureService 对同一个 Policy 每批只调用一次。实现必须批量校验 READ 的 fileId 确实来自目标业务资源，并
     * 校验 UPLOAD 对目标资源的修改资格；禁止按 item 逐条查询形成 N+1。无权访问时直接抛出业务异常，不能
     * 静默跳过。
     *
     * @param auth 当前请求认证快照；匿名 READ 时可以不包含身份
     * @param items 当前实现负责的签名请求
     */
    void authorize(@NotNull CurrentRequest auth, @NotNull List<FileSignatureItem> items);
}
