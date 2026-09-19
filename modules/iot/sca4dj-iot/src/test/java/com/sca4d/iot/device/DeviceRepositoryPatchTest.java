package com.sca4d.iot.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iot.persistence.DeviceDetailsUpdate;
import com.sca4d.iot.persistence.mapper.DeviceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证 Device Repository 把业务值转换成字段更新意图并拒绝空更新。 */
class DeviceRepositoryPatchTest {

    @Test
    @DisplayName("展示名称写入值且 null 备注明确写为数据库 NULL")
    void resolvesDetailsIntoSetAndClearPatches() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        DeviceRepository repository = new DeviceRepository(mapper, sqlBatches());

        repository.updateDetails(21L, "Gateway", null);

        ArgumentCaptor<DeviceDetailsUpdate> update = ArgumentCaptor.forClass(DeviceDetailsUpdate.class);
        verify(mapper).updateDeviceDetails(eq(21L), update.capture());
        assertThat(update.getValue().displayName()).isEqualTo(Patch.set("Gateway"));
        assertThat(update.getValue().remark()).isEqualTo(Patch.clear());
    }

    @Test
    @DisplayName("空字符串备注保持 SET 值而不转成 CLEAR")
    void keepsEmptyRemarkAsSetValue() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        DeviceRepository repository = new DeviceRepository(mapper, sqlBatches());

        repository.updateDetails(22L, "Gateway", "");

        ArgumentCaptor<DeviceDetailsUpdate> update = ArgumentCaptor.forClass(DeviceDetailsUpdate.class);
        verify(mapper).updateDeviceDetails(eq(22L), update.capture());
        assertThat(update.getValue().remark().value()).isEmpty();
    }

    @Test
    @DisplayName("两个字段都为 IGNORE 时不调用 Mapper")
    void rejectsUpdateWithoutEffectiveColumns() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        DeviceRepository repository = new DeviceRepository(mapper, sqlBatches());
        DeviceDetailsUpdate update = new DeviceDetailsUpdate(Patch.ignore(), Patch.ignore());

        assertThatThrownBy(() -> repository.updateDetails(23L, update))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要修改一个字段");
        verify(mapper, never()).updateDeviceDetails(anyLong(), any(DeviceDetailsUpdate.class));
    }

    /** 创建使用默认保护上限的 SQL 批处理器。 */
    private static SqlBatches sqlBatches() {
        return new SqlBatches(new SqlBatchProperties());
    }
}
