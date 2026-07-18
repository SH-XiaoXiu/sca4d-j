package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.exception.ErrorCodeCatalog;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.system.error.ErrorCodeCatalogDescription;
import com.sca4d.system.service.DefaultErrorCodeService;
import org.junit.jupiter.api.Test;

/** 验证错误码目录按当前语言补充消息并保持固定排序。 */
class ErrorCodeServiceTest {

    @Test
    void shouldReturnCatalog() {
        ErrorCodeService service = new DefaultErrorCodeService(
                ErrorCodeCatalog.common(), LocalizedMessagesFixture.create());

        ErrorCodeCatalogDescription catalog = service.catalog();

        assertThat(catalog.getItems()).hasSize(CommonErrorCode.values().length);
        assertThat(catalog.getItems()).extracting("code").contains(CommonErrorCode.BAD_REQUEST.getCode());
        assertThat(catalog.getItems())
                .filteredOn(item -> item.getCode() == CommonErrorCode.BAD_REQUEST.getCode())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getMessageKey()).isEqualTo("error.common.bad-request");
                    assertThat(item.getMessage()).isNotBlank();
                });
        assertThat(catalog.getRanges()).singleElement().satisfies(range -> {
            assertThat(range.getSource()).isEqualTo(CommonErrorCode.class.getSimpleName());
            assertThat(range.getStart()).isEqualTo(0);
            assertThat(range.getEnd()).isEqualTo(1999);
        });
    }
}
