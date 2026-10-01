package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

/** 验证应用启动时检查 Controller 顶层响应数据的 ETag 声明。 */
class ResponseETagContractValidatorTest {

    @Test
    @DisplayName("错误 ETag 声明在 Controller 接收请求前失败")
    void rejectsInvalidControllerResponseDuringStartup() throws Exception {
        RequestMappingHandlerMapping mappings = mock(RequestMappingHandlerMapping.class);
        HandlerMethod handler = new HandlerMethod(
                new InvalidController(),
                InvalidController.class.getDeclaredMethod("get"));
        when(mappings.getHandlerMethods()).thenReturn(Map.of(RequestMappingInfo.paths("/invalid").build(), handler));

        ResponseETagContractValidator validator = new ResponseETagContractValidator(mappings);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须公开、无参数并返回 Version");
    }

    /** 声明测试用 Controller 返回类型。 */
    static class InvalidController {

        /** @return 包含错误 ETag 声明的响应 */
        public ApiResponse<InvalidBody> get() {
            return ApiResponse.ok(new InvalidBody());
        }
    }

    /** 声明不应进入请求处理阶段的错误 ETag 方法。 */
    static class InvalidBody {

        /** @return 固定测试版本 */
        @ETag
        private Version revision() {
            return new Version(1);
        }
    }
}
