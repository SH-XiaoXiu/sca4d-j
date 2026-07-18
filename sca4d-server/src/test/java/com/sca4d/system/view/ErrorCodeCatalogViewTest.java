package com.sca4d.system.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.exception.ErrorCodeCatalog;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.system.service.DefaultErrorCodeService;
import org.junit.jupiter.api.Test;

/** 验证错误码目录转换为管理端分组视图。 */
class ErrorCodeCatalogViewTest {

    @Test
    void shouldCreateViewFromCatalog() {
        ErrorCodeCatalogView view = ErrorCodeCatalogView.from(new DefaultErrorCodeService(
                ErrorCodeCatalog.common(), LocalizedMessagesFixture.create())
                .catalog());

        assertThat(view.getTotal()).isEqualTo(CommonErrorCode.values().length);
        assertThat(view.getItems()).extracting("code").contains(CommonErrorCode.BAD_REQUEST.getCode());
        assertThat(view.getRanges()).singleElement().satisfies(range -> {
            assertThat(range.getSource()).isEqualTo(CommonErrorCode.class.getSimpleName());
            assertThat(range.getStart()).isEqualTo(0);
            assertThat(range.getEnd()).isEqualTo(1999);
        });
    }
}
