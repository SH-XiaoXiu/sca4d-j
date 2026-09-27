package com.sca4d.app.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.plugin.ApiBinding;
import com.sca4d.core.api.plugin.ApiContract;
import com.sca4d.core.api.plugin.ApiInvoker;
import com.sca4dj.gradle.plugin.processor.PluginApiProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** 用真实 javac 验证三种声明及非法契约诊断，不把生成源码手写到测试中。 */
class PluginCodeGenerationTest {
    @TempDir
    Path temporary;

    @Test
    @DisplayName("已有类方法生成独立接口和宿主绑定，客户端能直接调用实际目标")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generatesClassServiceAndBinding() throws Throwable {
        String source = """
                package fixture;
                import com.sca4d.core.api.plugin.*;
                @PluginService("TEST:SERVICE")
                public class SourceService {
                    @PluginOperation("READ")
                    public String read(String value) { return "host:" + value; }
                }
                """;
        assertTrue(compile("SourceService", source));
        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[] {temporary.toUri().toURL()},
                ApiContract.class.getClassLoader())) {
            Class<?> implementation = loader.loadClass("fixture.SourceService");
            Object host = implementation.getConstructor().newInstance();
            ApiBinding binding = (ApiBinding) loader.loadClass("fixture.SourceServiceApiBinding")
                    .getMethod("bind", implementation)
                    .invoke(null, host);
            ApiContract contract = (ApiContract) loader.loadClass("fixture.SourceServiceApiContract")
                    .getField("INSTANCE")
                    .get(null);
            ApiInvoker invoker = (target, operation, arguments) -> binding.operations()
                    .get(operation)
                    .invoke(arguments);
            Object client = contract.client(invoker, null);
            assertEquals("host:value", contract.type().getMethod("read", String.class).invoke(client, "value"));
            String exported = Files.readString(temporary.resolve("META-INF/sca4dj/api-classes.list"));
            assertTrue(exported.contains("fixture.SourceServiceApi\n"));
            assertFalse(exported.contains("fixture.SourceService\n"));
        }
    }

    @Test
    @DisplayName("接口契约生成可绑定普通适配器的客户端和分发器")
    void generatesInterfaceContract() throws Exception {
        assertTrue(compile("Bridge", """
                package fixture;
                import com.sca4d.core.api.plugin.*;
                @PluginService("TEST:BRIDGE")
                public interface Bridge {
                    @PluginOperation("COUNT") long count(String value);
                }
                """));
        assertTrue(Files.exists(temporary.resolve("fixture/BridgeContract.class")));
    }

    @Test
    @DisplayName("构建拒绝任意 Object 和重复操作编码")
    void rejectsUncheckableAndDuplicateContracts() throws Exception {
        assertFalse(compile("Unsafe", """
                package fixture;
                import com.sca4d.core.api.plugin.*;
                @PluginExtension("TEST:UNSAFE")
                public interface Unsafe {
                    @PluginOperation("READ") Object read();
                }
                """));
        assertFalse(compile("Duplicate", """
                package fixture;
                import com.sca4d.core.api.plugin.*;
                @PluginService("TEST:DUPLICATE")
                public interface Duplicate {
                    @PluginOperation("READ") String first();
                    @PluginOperation("READ") String second();
                }
                """));
    }

    /** 在独立输出中编译声明与生成代码，使用实际 Core API 的类型。 */
    private boolean compile(String name, String source) throws Exception {
        Path file = temporary.resolve(name + ".java");
        Files.writeString(file, source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            String classpath = Path.of(ApiContract.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
            var task = compiler.getTask(
                    null,
                    manager,
                    diagnostics,
                    List.of(
                            "-classpath",
                            classpath,
                            "-d",
                            temporary.toString(),
                            "-s",
                            temporary.resolve("sources").toString()),
                    null,
                    manager.getJavaFileObjects(file.toFile()));
            task.setProcessors(List.of(new PluginApiProcessor()));
            return task.call();
        }
    }
}
