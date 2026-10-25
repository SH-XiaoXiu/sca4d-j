package com.sca4d.core.spring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.core.spring.call.CallHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/** 验证 IdempotencyKeyResolver 从 Call 输入读取幂等键并校验格式，HttpCallFilter 把请求头登记为输入，以及 MethodArgumentsIdempotencyKeyResolver 计算的方法参数摘要。 */
class IdempotencyKeyResolverTest {

    @BeforeAll
    static void initializeHttpConstants() {
        ConfiguredConstants.initialize(HttpConstants.class, ignored -> null);
    }

    /** 当前测试使用的 Call；bindHttpInput 会用携带 HTTP 输入的新 Call 替换它。 */
    private Call call = Call
            .system("idempotency-test", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));

    @Test
    @DisplayName("标准 Call 输入校验并去除客户端幂等键首尾空白")
    void resolvesHttpHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpConstants.IDEMPOTENCY_KEY_HEADER, "  create-account-1  ");
        bindHttpInput(request);

        assertEquals(
                "create-account-1",
                IdempotencyKey.fromValue(call.input(IdempotencyKey.INPUT).orElseThrow()).value());
    }

    @Test
    @DisplayName("标准 Call 输入拒绝缺失和含控制字符的幂等键")
    void rejectsInvalidHttpHeader() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        bindHttpInput(missing);
        assertThrows(
                IllegalArgumentException.class,
                () -> call.input(IdempotencyKey.INPUT)
                        .orElseThrow(() -> new IllegalArgumentException("当前调用没有提供幂等键")));

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader(HttpConstants.IDEMPOTENCY_KEY_HEADER, "create\u0001account");
        bindHttpInput(invalid);
        assertThrows(
                IllegalArgumentException.class,
                () -> IdempotencyKey.fromValue(call.input(IdempotencyKey.INPUT).orElseThrow()));
    }

    @Test
    @DisplayName("HTTP Filter 把客户端幂等键登记为标准 Call 输入")
    void capturesHttpIdempotencyKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/test");
        request.addHeader(HttpConstants.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new HttpCallFilter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> assertEquals(
                        "key-1",
                        CallHolder.current().input(IdempotencyKey.INPUT).orElseThrow()));
        assertEquals(null, CallHolder.currentOrNull());
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

    /** 创建指向 invoke(Object) 的 CallExecution，参数为给定 arguments，供方法参数解析器读取。 */
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
        String header = request.getHeader(HttpConstants.IDEMPOTENCY_KEY_HEADER);
        if (header != null) {
            inputs.put(IdempotencyKey.INPUT, header);
        }
        call = new Call("http-input", Instant.EPOCH, inputs.build(), null);
    }

    /** 仅作为 execution 中反射获取 Method 的目标，方法体不会被调用。 */
    private void invoke(Object value) {
        Objects.requireNonNull(value);
    }
}
