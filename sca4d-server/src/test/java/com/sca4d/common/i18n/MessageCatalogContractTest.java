package com.sca4d.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.exception.ResponseCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 验证拆分后的消息资源不存在重复键，并为每个英文消息提供完整翻译。 */
class MessageCatalogContractTest {

    private static final List<String> BASENAMES = List.of(
            "common", "error", "permission", "audit", "dictionary", "resource", "navigation");

    @Test
    void shouldProvideUniqueProductionMessageKeysInRootAndEnglishBundles() throws Exception {
        Path resources = Path.of("src/main/resources/i18n");
        Set<String> rootKeys = new LinkedHashSet<>();
        Set<String> englishKeys = new LinkedHashSet<>();
        for (String basename : BASENAMES) {
            Properties root = load(resources.resolve(basename + ".properties"));
            Properties english = load(resources.resolve(basename + "_en_US.properties"));

            assertThat(root.stringPropertyNames())
                    .as("%s 的中英文消息键必须完全一致", basename)
                    .containsExactlyInAnyOrderElementsOf(english.stringPropertyNames());
            assertThat(root.stringPropertyNames())
                    .allSatisfy(key -> assertThat(root.getProperty(key))
                            .as("%s 缺少中文消息 %s", basename, key)
                            .isNotBlank());
            assertThat(english.stringPropertyNames())
                    .allSatisfy(key -> assertThat(english.getProperty(key))
                            .as("%s 缺少英文消息 %s", basename, key)
                            .isNotBlank()
                            .doesNotContainPattern("[\\p{IsHan}]"));

            Set<String> duplicateRootKeys = new LinkedHashSet<>(rootKeys);
            duplicateRootKeys.retainAll(root.stringPropertyNames());
            assertThat(duplicateRootKeys)
                    .as("%s 与其他 basename 不得定义相同消息键", basename)
                    .isEmpty();
            Set<String> duplicateEnglishKeys = new LinkedHashSet<>(englishKeys);
            duplicateEnglishKeys.retainAll(english.stringPropertyNames());
            assertThat(duplicateEnglishKeys)
                    .as("%s 的英文资源与其他 basename 不得定义相同消息键", basename)
                    .isEmpty();
            rootKeys.addAll(root.stringPropertyNames());
            englishKeys.addAll(english.stringPropertyNames());
        }

        try (var files = Files.list(resources)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .noneMatch(name -> name.endsWith("_zh_CN.properties"));
        }
        assertThat(englishKeys).containsExactlyInAnyOrderElementsOf(rootKeys);

        Set<String> requiredKeys = new LinkedHashSet<>();
        for (ResponseCode responseCode : ResponseCode.values()) {
            requiredKeys.add(responseCode.getMessageKey());
        }
        Pattern messageKey = Pattern.compile(
                "\"((?:audit|error|navigation|response|validation)\\.[a-z0-9.-]*[a-z0-9-])\"");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = messageKey.matcher(Files.readString(file));
                while (matcher.find()) {
                    requiredKeys.add(matcher.group(1));
                }
            }
        }

        assertThat(rootKeys)
                .as("全部生产审计、错误、导航、响应和校验消息键必须存在于拆分后的根资源")
                .containsAll(requiredKeys);
    }

    private Properties load(Path path) throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
