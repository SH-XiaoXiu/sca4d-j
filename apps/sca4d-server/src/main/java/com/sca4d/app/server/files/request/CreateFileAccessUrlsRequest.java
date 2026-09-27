package com.sca4d.app.server.files.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 接收一次批量生成短期读取地址的文件标识。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public class CreateFileAccessUrlsRequest {

    @Req.NotNull
    @Size(min = 1, max = 100)
    private @Nullable List<@Req.NotNull @NotNull Long> fileIds;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public CreateFileAccessUrlsRequest() {
    }

    /**
     * 返回需要生成读取地址的文件标识。
     *
     * @return 一至一百个文件标识
     */
    public @NotNull @Unmodifiable List<@NotNull Long> getFileIds() {
        return List.copyOf(Objects.requireNonNull(fileIds, "文件标识不能为空"));
    }

    /**
     * 设置需要生成读取地址的文件标识。
     *
     * @param fileIds 一至一百个文件标识
     */
    public void setFileIds(@Nullable List<@NotNull Long> fileIds) {
        this.fileIds = fileIds == null ? null : List.copyOf(fileIds);
    }
}
