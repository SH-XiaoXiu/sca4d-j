package com.sca4d.iot.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.iot.api.protocol.DeviceProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证应用安装协议的稳定索引和管理查询结果。 */
class DeviceProtocolCatalogTest {

    @Test
    @DisplayName("按协议 code 排列全部已安装协议")
    void findsInstalledProtocolsInStableOrder() {
        DeviceProtocol later = mock(DeviceProtocol.class);
        DeviceProtocol earlier = mock(DeviceProtocol.class);
        when(later.code()).thenReturn("z-protocol");
        when(earlier.code()).thenReturn("a-protocol");

        DeviceProtocolCatalog catalog = new DeviceProtocolCatalog(List.of(later, earlier));

        assertThat(catalog.findAll()).extracting(DeviceProtocol::code)
                .containsExactly("a-protocol", "z-protocol");
    }
}
