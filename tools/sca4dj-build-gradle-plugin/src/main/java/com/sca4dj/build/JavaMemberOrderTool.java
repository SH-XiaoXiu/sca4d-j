package com.sca4dj.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 让构建插件自身的 Spotless 任务使用固定的字段排序规则。 */
final class JavaMemberOrderTool {
    private JavaMemberOrderTool() {
    }

    /** 以 apply 写入或以 check 检查指定源码目录。 */
    public static void main(String[] args) {
        if (args.length != 2 || (!args[0].equals("apply") && !args[0].equals("check"))) {
            throw new IllegalArgumentException("用法: JavaMemberOrderTool <apply|check> <source-directory>");
        }
        try (var paths = Files.walk(Path.of(args[1]))) {
            List<Path> files = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String ordered = JavaMemberOrderStep.format(source, JavaMemberOrderStep.defaultGroups());
                if (!ordered.equals(source)) {
                    if (args[0].equals("check")) {
                        throw new IllegalStateException("Java 字段顺序不符合脚手架规则: " + file);
                    }
                    Files.writeString(file, ordered, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("无法检查 Java 字段顺序", exception);
        }
    }
}
