package com.sca4d.core.spring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

class IdempotencyKeyResolverTest {

    private Call call = Call
            .system("idempotency-test", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("HTTP 解析器校验并去除幂等键首尾空白")
    void resolvesHttpHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpIdempotencyKeyResolver.HEADER, "  create-account-1  ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        bindHttpInput(request);

        assertEquals(
                "create-account-1",
                new HttpIdempotencyKeyResolver().resolve(execution(new Object[0])).value());
    }

    @Test
    @DisplayName("HTTP 解析器拒绝缺失和含控制字符的幂等键")
    void rejectsInvalidHttpHeader() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(missing));
        bindHttpInput(missing);
        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpIdempotencyKeyResolver().resolve(execution(new Object[0])));

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader(HttpIdempotencyKeyResolver.HEADER, "create\u0001account");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(invalid));
        bindHttpInput(invalid);
        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpIdempotencyKeyResolver().resolve(execution(new Object[0])));
    }

    @Test
    @DisplayName("方法参数解析器只返回规范化参数摘要")
    void resolvesMethodArgumentDigest() {
        IdempotencyFingerprints fingerprints = new IdempotencyFingerprints(new ObjectMapper());
        Object[] arguments = {Map.of("name", "account", "enabled", true)};

        assertEquals(
                fingerprints.argumentsDigest(arguments),
                new MethodArgumentsIdempotencyKeyResolver(fingerprints)
                        .resolve(execution(arguments))
                        .value());
    }

    private CallExecution execution(Object[] arguments) {
        try {
            Method method = getClass().getDeclaredMethod("invoke", Object.class);
            return new CallExecution(call, getClass(), method, arguments, method.getGenericReturnType());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    /** 模拟 HTTP Filter 采集不可变数据，解析器不再依赖 Servlet ThreadLocal。 */
    private void bindHttpInput(MockHttpServletRequest request) {
        DataValues.Builder inputs = DataValues.builder()
                .put(HttpCall.KEY, new HttpCall("POST", "/test", "127.0.0.1", null));
        String header = request.getHeader(HttpIdempotencyKeyResolver.HEADER);
        if (header != null) {
            inputs.put(HttpIdempotencyKeyResolver.KEY, header);
        }
        call = new Call("http-input", Instant.EPOCH, inputs.build(), null);
        RequestContextHolder.resetRequestAttributes();
    }

    private void invoke(Object value) {
        Objects.requireNonNull(value);
    }
}
