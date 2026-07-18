package com.sca4d.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** 验证 Gateway 生产与测试源码遵守项目级 Java 类型禁令。 */
class SourceContractTest {

    @Test
    void productionAndTestCodeShouldNotDeclareRecords() throws Exception {
        Pattern declaration = Pattern.compile(
                "(?m)^\\s*(?:(?:public|protected|private|static|final|sealed|non-sealed)\\s+)*"
                        + "record\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(");

        String production;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            production = files.filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException("读取文件失败: " + path, exception);
                        }
                    })
                    .reduce("", String::concat);
        }
        String tests;
        try (var files = Files.walk(Path.of("src/test/java"))) {
            tests = files.filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException("读取文件失败: " + path, exception);
                        }
                    })
                    .reduce("", String::concat);
        }

        assertThat(declaration.matcher(production).find()).isFalse();
        assertThat(declaration.matcher(tests).find()).isFalse();
    }
}
