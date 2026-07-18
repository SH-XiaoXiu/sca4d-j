package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.exception.ErrorCodeCatalog;
import com.sca4d.common.exception.ResponseCode;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.system.service.DefaultErrorCodeService;
import org.junit.jupiter.api.Test;

/** 验证错误码目录接口返回本地化消息和号段信息。 */
class ErrorCodeControllerTest {

    @Test
    void shouldReturnCatalogApiResponse() {
        ErrorCodeController controller = new ErrorCodeController(new DefaultErrorCodeService(
                ErrorCodeCatalog.common(), LocalizedMessagesFixture.create()));

        var response = controller.catalog();

        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getTotal()).isEqualTo(CommonErrorCode.values().length);
    }
}
