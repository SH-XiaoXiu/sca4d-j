package com.sca4d.app.server.files;

import com.sca4d.app.server.files.request.CreateFileAccessUrlsRequest;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.storage.StoredFile;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** 从完成上传的文件标识或批量读取请求建立文件资源集合。 */
@Component
public class FileResourceResolver implements CallValueResolver<Collection<Resource>> {
    /** 创建无状态的文件资源解析器。 */
    public FileResourceResolver() {
    }

    /**
     * 使用第一个方法实参中的文件标识或批量请求建立去重资源集合。
     *
     * @param execution 当前文件方法执行
     * @return 与输入顺序一致的非空文件资源集合
     */
    @Override
    public @NotNull Collection<Resource> resolve(@NotNull CallExecution execution) {
        Object[] arguments = execution.arguments();
        if (arguments.length == 0) {
            throw new IllegalStateException("文件资源解析缺少文件标识或批量请求");
        }
        if (arguments[0] instanceof Number fileId) {
            return List.of(
                    new Resource.Ref(
                            StoredFile.RESOURCE_TYPE,
                            Long.toString(fileId.longValue())));
        }
        if (arguments[0] instanceof CreateFileAccessUrlsRequest request) {
            return request.getFileIds()
                    .stream()
                    .distinct()
                    .<Resource>map(
                            fileId -> new Resource.Ref(
                                    StoredFile.RESOURCE_TYPE,
                                    Long.toString(fileId)))
                    .toList();
        }
        throw new IllegalStateException("文件资源解析要求第一个实参为数字文件标识或批量读取请求");
    }
}
