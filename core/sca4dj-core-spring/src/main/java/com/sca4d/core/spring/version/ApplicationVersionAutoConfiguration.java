package com.sca4d.core.spring.version;

import com.sca4d.core.api.version.ApplicationVersion;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Properties;

/** 从构件内部读取版本，部署环境变量不能改写构建事实。 */
@AutoConfiguration
@ConditionalOnResource(resources = {"classpath:META-INF/build-info.properties", "classpath:build-manifest.json"})
public class ApplicationVersionAutoConfiguration {
    /** @return 当前构件的不可变版本 */
    @Bean
    @ConditionalOnMissingBean
    ApplicationVersion applicationVersion() throws IOException {
        var properties = new Properties();
        try (var input = new ClassPathResource("META-INF/build-info.properties").getInputStream()) {
            properties.load(input);
        }
        var fields = new LinkedHashMap<String, String>();
        properties.stringPropertyNames()
                .stream()
                .filter(key -> key.startsWith("build."))
                .forEach(key -> fields.put(key.substring(6), properties.getProperty(key)));
        return new ApplicationVersion(fields);
    }
}
