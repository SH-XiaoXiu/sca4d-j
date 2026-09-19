package com.sca4d.iot.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证物模型命令同时保存命令 code、策略和类型化输入键。 */
class ThingCommandTest {

    private static final AcknowledgementPolicy ACKNOWLEDGEMENT = new AcknowledgementPolicy() {
        @Override
        public @NotNull String code() {
            return "ack";
        }

        @Override
        public boolean requiresAcknowledgement() {
            return false;
        }

        @Override
        public @NotNull AcknowledgementDecision evaluate(
                @NotNull DeviceDownlinkMessage message,
                @NotNull DeviceAcknowledgement acknowledgement,
                @NotNull Instant now) {
            return AcknowledgementDecision.succeed();
        }
    };

    private static final RetryPolicy RETRY = new RetryPolicy() {
        @Override
        public @NotNull String code() {
            return "retry";
        }

        @Override
        public @NotNull RetryDecision evaluate(
                @NotNull DeviceDownlinkMessage message,
                @NotNull IotFailureCode failure,
                @NotNull Instant now) {
            return RetryDecision.stop(failure);
        }
    };

    @Test
    void keepsTypedInputKeys() {
        DataKey<Long> level = DataKey.of("level", Long.class);
        ThingCommand command = ThingCommand.builder("set-level")
                .input(
                        level,
                        ThingValueType.integerType()
                                .minimum("0")
                                .maximum("100")
                                .build())
                .acknowledgementPolicy(ACKNOWLEDGEMENT)
                .retryPolicy(RETRY)
                .build();

        assertEquals(java.util.Set.of("level"), command.inputs().keySet());
        assertSame(level, command.inputKeys().get("level"));
    }

    @Test
    void createsStandardKeyForStringDeclaration() {
        ThingCommand command = ThingCommand.builder("set-mode")
                .input("mode", ThingValueType.enumType().value("on").build())
                .acknowledgementPolicy(ACKNOWLEDGEMENT)
                .retryPolicy(RETRY)
                .build();

        assertEquals(String.class, command.inputKeys().get("mode").type());
    }

    @Test
    void rejectsDataKeyWithDifferentJavaType() {
        DataKey<Integer> level = DataKey.of("level", Integer.class);
        ThingValueType integer = ThingValueType.integerType().build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ThingCommand.builder("set-level").input(level, integer));
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("DataKey Java 类型与物模型不一致"));
    }
}
