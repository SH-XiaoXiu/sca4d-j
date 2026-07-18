package com.sca4d.config;

import com.sca4d.common.dictionary.CodeDictionaryCatalog;
import com.sca4d.common.dictionary.CodeDictionaryScanner;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 代码枚举字典扫描、校验和目录装配配置。 */
@Configuration
public class DictionaryConfig {

    /**
     * 注册显式代码字典枚举扫描器。
     *
     * @return 代码字典扫描器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull CodeDictionaryScanner codeDictionaryScanner() {
        return new CodeDictionaryScanner();
    }

    /**
     * 从 Spring Boot 应用基础包构建代码字典目录。
     *
     * <p>应用基础包天然覆盖主应用和其业务子包，不增加与错误码扫描重复的环境配置入口。
     *
     * @param scanner 代码字典扫描器
     * @param applicationContext Spring 应用上下文
     * @return 当前应用全部显式代码字典的不可变目录
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull CodeDictionaryCatalog codeDictionaryCatalog(
            @NotNull CodeDictionaryScanner scanner, @NotNull ApplicationContext applicationContext) {
        List<String> packages = AutoConfigurationPackages.get(applicationContext);
        Set<String> basePackages = new LinkedHashSet<>(packages);
        return new CodeDictionaryCatalog(scanner.scan(basePackages));
    }
}
