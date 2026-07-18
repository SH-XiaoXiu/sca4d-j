package com.sca4d.system.service;

import com.sca4d.common.exception.ErrorCodeCatalog;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.system.ErrorCodeService;
import com.sca4d.system.error.ErrorCodeCatalogDescription;
import com.sca4d.system.error.ErrorCodeDescription;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;

/** 使用启动期错误码快照和共用语言目录提供错误码说明的默认实现。 */
@Service
@RequiredArgsConstructor
public class DefaultErrorCodeService implements ErrorCodeService {

    private final ErrorCodeCatalog catalog;
    private final LocalizedMessages messages;

    @Override
    public @NotNull ErrorCodeCatalogDescription catalog() {
        // 目录完全来自内存快照；每个错误码只执行一次本地消息资源查找，不访问数据库或外部服务。
        List<ErrorCodeDescription> items = catalog.items().stream()
                .map(item -> new ErrorCodeDescription(
                        item.getCode(),
                        item.getMessageKey(),
                        messages.get(item.getMessageKey()),
                        item.getCategory(),
                        item.getHttpStatus(),
                        item.getSource()))
                .toList();
        return new ErrorCodeCatalogDescription(items, catalog.ranges());
    }
}
