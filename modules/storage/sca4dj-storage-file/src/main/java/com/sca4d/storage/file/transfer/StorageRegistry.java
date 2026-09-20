package com.sca4d.storage.file.transfer;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按文件记录中的 backendId 查找存储实现，并拒绝两个实现使用相同标识。 */
@ApiStatus.Internal
public final class StorageRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(StorageRegistry.class);

    private final @NotNull Map<String, FileStorage> storages;

    /** 校验实现标识、可用性和默认实现，并建立历史文件使用的实现索引。 */
    @SuppressWarnings("ExtractMethodRecommender") // 连续展示注册、默认值和可用性检查的启动失败顺序。
    public StorageRegistry(
            @NotNull List<@NotNull FileStorage> values,
            @NotNull String defaultBackendId) {
        Map<String, FileStorage> indexed = new LinkedHashMap<>();
        for (FileStorage value : Objects.requireNonNull(values, "存储实现不能为空")) {
            FileStorage storage = Objects.requireNonNull(value, "存储实现不能为空");
            String backendId = Objects.requireNonNull(storage.backendId(), "存储 backendId 不能为空");
            if (backendId.isBlank() || !backendId.equals(backendId.trim())) {
                throw new IllegalStateException("存储 backendId 不能为空白或包含首尾空白");
            }
            FileStorage duplicate = indexed.putIfAbsent(backendId, storage);
            if (duplicate != null) {
                throw new IllegalStateException("存储 backendId 重复: " + backendId);
            }
        }
        String checkedDefault = Objects.requireNonNull(defaultBackendId, "默认存储 backendId 不能为空");
        if (!indexed.containsKey(checkedDefault)) {
            throw new IllegalStateException("默认存储 backendId 未注册: " + checkedDefault);
        }
        for (FileStorage storage : indexed.values()) {
            storage.checkAvailable();
            LOG.info(
                    "文件存储实现可用 | backendId={} container={}",
                    storage.backendId(),
                    storage.defaultContainer());
        }
        this.storages = Map.copyOf(indexed);
    }

    /** 按文件记录保存的标识返回存储实现；未注册时明确报告存储不可用。 */
    @NotNull
    public FileStorage storage(@NotNull String id) {
        FileStorage storage = storages.get(id);
        if (storage == null) {
            throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE);
        }
        return storage;
    }
}
