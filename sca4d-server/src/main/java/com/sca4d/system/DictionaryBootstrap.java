package com.sca4d.system;

import com.sca4d.common.dictionary.CodeDictionaryCatalog;
import com.sca4d.common.dictionary.CodeDictionaryOption;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.system.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 在应用启动完成后把代码枚举同步为系统字典只读投影。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DictionaryBootstrap {

    private final CodeDictionaryCatalog catalog;
    private final DictionaryRepository repository;
    private final LocalizedMessages messages;

    /**
     * 在 Flyway、Mapper 和业务 Bean 均已就绪后同步当前代码字典目录。
     *
     * @param event Spring 应用完成启动并准备接收流量的事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void synchronize(ApplicationReadyEvent event) {
        long startedAt = System.nanoTime();
        log.info("代码字典启动同步开始 | dictionaryCount={} localeCount={}",
                catalog.definitions().size(), SupportedLocale.values().length);
        // 每种支持语言分别读取一次类型名称和每个枚举项标签；缺失消息键时拒绝启动，避免运行期回显技术编码。
        for (SupportedLocale locale : SupportedLocale.values()) {
            catalog.definitions().forEach(definition -> {
                messages.get(locale.locale(), definition.getNameKey());
                definition.getOptions().stream()
                        .filter(CodeDictionaryOption::isPublished)
                        .forEach(option -> messages.get(locale.locale(), Objects.requireNonNull(option.getLabelKey())));
            });
        }
        repository.synchronizeCodeCatalog(catalog);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "代码字典启动同步完成 | dictionaryCount={} localeCount={} durationMs={}",
                catalog.definitions().size(),
                SupportedLocale.values().length,
                durationMs);
    }
}
