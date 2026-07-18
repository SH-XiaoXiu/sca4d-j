package com.sca4d.system;

import com.sca4d.system.error.ErrorCodeCatalogDescription;
import org.jetbrains.annotations.NotNull;

/**
 * 错误码目录读取服务。
 *
 * <p>目录由应用启动时已经完成号段和冲突校验的错误码定义生成；读取时按当前请求语言补充展示消息。
 */
public interface ErrorCodeService {

    /**
     * 获取错误码目录。
     *
     * <p>目录完全来自应用启动时校验完成的内存快照，每次请求只按错误码数量执行消息资源查找，不访问数据库或外部服务。
     *
     * @return 按当前请求语言解析的错误码目录
     */
    @NotNull
    ErrorCodeCatalogDescription catalog();
}
