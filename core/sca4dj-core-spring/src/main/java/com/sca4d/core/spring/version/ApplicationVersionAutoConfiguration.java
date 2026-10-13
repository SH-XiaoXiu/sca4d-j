package com.sca4d.core.spring.version;

import com.sca4d.core.api.version.ApplicationVersion;
import com.sca4d.core.api.version.CompatibilityResolver;
import com.sca4d.core.api.version.CompatibilityRule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

/** 从应用配置创建版本和兼容规则；不依赖构建工具、产物格式或固定文件位置。 */
@AutoConfiguration
public class ApplicationVersionAutoConfiguration {
    /** @return 已解析配置变量且通过版本规则校验的启动快照 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.version", name = "enabled", havingValue = "true")
    ApplicationVersion applicationVersion(Environment environment) {
        return Binder.get(environment)
                .bind("sca4dj.version", Bindable.of(ApplicationVersion.class))
                .orElseThrow(() -> new IllegalStateException("缺少应用版本配置"));
    }

    /** @return 应用声明的静态兼容矩阵；版本范围重叠或缺少规则时启动失败 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.versioning", name = "enabled", havingValue = "true")
    CompatibilityResolver compatibilityResolver(Environment environment) {
        List<CompatibilityRule> rules = Binder.get(environment)
                .bind("sca4dj.versioning.clients", Bindable.listOf(CompatibilityRule.class))
                .orElseThrow(() -> new IllegalStateException("缺少兼容矩阵配置"));
        if (rules.isEmpty())
            throw new IllegalStateException("兼容矩阵不能为空");
        return new CompatibilityResolver(rules);
    }
}
