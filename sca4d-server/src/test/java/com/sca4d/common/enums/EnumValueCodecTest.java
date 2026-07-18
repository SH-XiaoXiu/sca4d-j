package com.sca4d.common.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.core.EnabledStatus;
import com.sca4d.common.core.SortDirection;
import org.junit.jupiter.api.Test;

/** 验证枚举字符串的转换、解码和未知值拒绝。 */
class EnumValueCodecTest {

    @Test
    void shouldEncodeBaseEnumValue() {
        assertThat(EnumValueCodec.encode(EnabledStatus.ENABLED)).isEqualTo("ENABLED");
        assertThat(EnumValueCodec.encode(null)).isNull();
    }

    @Test
    void shouldDecodeBaseEnumByValue() {
        assertThat(EnumValueCodec.decode(SortDirection.class, "desc")).isEqualTo(SortDirection.DESC);
        assertThat(EnumValueCodec.decode(SortDirection.class, null)).isNull();
    }

    @Test
    void shouldDecodePlainEnumByName() {
        assertThat(EnumValueCodec.decode(PlainStatus.class, "ready")).isEqualTo(PlainStatus.READY);
    }

    @Test
    void shouldRejectUnknownValue() {
        assertThatThrownBy(() -> EnumValueCodec.decode(SortDirection.class, "unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 未实现 BaseEnum 时用于验证普通 Enum 解码的枚举。 */
    private enum PlainStatus {
        /** 唯一可解码的普通枚举值。 */
        READY
    }
}
