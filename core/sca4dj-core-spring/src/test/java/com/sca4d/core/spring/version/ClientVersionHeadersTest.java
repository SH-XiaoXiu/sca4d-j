package com.sca4d.core.spring.version;

import static org.assertj.core.api.Assertions.*;

import com.sca4d.core.api.version.ClientVersion;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.function.Function;

/** 在隔离的进程常量空间验证应用可以覆盖协议头且不能在启动后改变名称。 */
class ClientVersionHeadersTest {
    @Test
    void consumesApplicationConfiguredHeadersInsteadOfFixedNames() throws Exception {
        var loader = new ConstantsLoader();
        Class<?> constants = initialize(
                loader,
                Map.of(
                        "sca4dj.http.headers.client-name",
                        "X-App-Name",
                        "sca4dj.http.headers.client-release-version",
                        "X-App-Version",
                        "sca4dj.http.headers.client-api-contract",
                        "X-App-Contract"));
        constants.getMethod("validate").invoke(null);
        Object resolver = loader.loadClass(ClientVersionResolver.class.getName()).getConstructor().newInstance();
        var resolve = resolver.getClass().getMethod("resolve", HttpServletRequest.class);
        var request = new MockHttpServletRequest();
        request.addHeader("X-App-Name", "mobile");
        request.addHeader("X-App-Version", "dev-abc123");
        request.addHeader("X-App-Contract", "2");
        // 原来的默认头不能覆盖 App 已选择的协议。
        request.addHeader("X-Client-Name", "wrong");
        request.addHeader("X-Client-Release-Version", "invalid");
        var client = (ClientVersion) resolve.invoke(resolver, request);
        assertThat(client.name()).isEqualTo("mobile");
        assertThat(client.releaseVersion()).isEqualTo("dev-abc123");
        assertThat(client.contract()).isEqualTo(2);
        assertThatThrownBy(() -> initialize(loader, Map.of("sca4dj.http.headers.client-name", "X-Changed")))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsReservedAndConflictingNamesAtStartup() throws Exception {
        for (String name : new String[] {"Authorization", "x-call-id", "Bad Header", "X-Gateway-Secret", "Vary",
                "Access-Control-Expose-Headers"}) {
            Class<?> constants = initialize(new ConstantsLoader(), Map.of("sca4dj.http.headers.client-name", name));
            assertThatThrownBy(() -> constants.getMethod("validate").invoke(null))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    private static Class<?> initialize(ClassLoader loader, Map<String, String> values) throws Exception {
        Class<?> configured = loader.loadClass("com.sca4d.core.api.setting.ConfiguredConstants");
        var initializer = configured.getMethod("initialize", Class.class, Function.class);
        initializer.invoke(
                null,
                loader.loadClass("com.sca4d.core.api.gateway.GatewayConstants"),
                (Function<String, String>) ignored -> null);
        Class<?> constants = loader.loadClass("com.sca4d.core.spring.web.HttpConstants");
        initializer.invoke(null, constants, (Function<String, String>) values::get);
        return constants;
    }

    /** 每次测试模拟一次独立启动，避免全局常量覆盖影响其他测试。 */
    private static final class ConstantsLoader extends ClassLoader {
        ConstantsLoader() {
            super(ClientVersionHeadersTest.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.sca4d.core.api.setting.ConfiguredConstants")
                    && !name.equals("com.sca4d.core.api.gateway.GatewayConstants")
                    && !name.equals("com.sca4d.core.spring.web.HttpConstants")
                    && !name.equals(ClientVersionResolver.class.getName()))
                return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try (var source = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (source == null)
                        throw new ClassNotFoundException(name);
                    byte[] bytes = source.readAllBytes();
                    type = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException exception) {
                    throw new ClassNotFoundException(name, exception);
                }
            }
            if (resolve)
                resolveClass(type);
            return type;
        }
    }
}
