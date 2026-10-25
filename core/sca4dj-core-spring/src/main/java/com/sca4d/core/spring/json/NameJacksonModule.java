package com.sca4d.core.spring.json;

import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Function;

/** 在 JSON 字符串读写时恢复名称类型；Code 保持业务编码，只有 Key 按 App 隔离输出。 */
public class NameJacksonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    /** 注册公共名称类型及各自协议约定的输出格式，不根据 JSON 字段名推断 App 隔离规则。 */
    public NameJacksonModule() {
        super("sca4dj-names");
        register(Name.class, Name::new, NameFormat.COLON);
        addSerializer(Code.class, new NameSerializer<>(Code.class, NameFormat.COLON));
        register(TreeCode.class, TreeCode::of, NameFormat.COLON);
        register(Key.class, text -> Key.parse(text, NameFormat.COLON), NameFormat.COLON);
        register(JobKey.class, JobKey::new, NameFormat.SLASH);
        register(ScheduleKey.class, ScheduleKey::new, NameFormat.SLASH);
        register(GatewayCallbackCode.class, GatewayCallbackCode::new, NameFormat.DOT);
        register(AuthenticationActionCode.class, AuthenticationActionCode::new, NameFormat.DOT);
        register(AccountIdentifierType.class, AccountIdentifierType::new, NameFormat.DOT);
    }

    /** 为一个名称类型登记精确解析器和该 JSON 协议使用的输出格式。 */
    private <T extends Name> void register(
            @NotNull Class<T> type,
            @NotNull Function<String, T> factory,
            @NotNull NameFormat format) {
        addSerializer(type, new NameSerializer<>(type, format));
        addDeserializer(type, new NameDeserializer<>(type, factory));
    }

    /** 将类型化名称输出为协议声明的 JSON 字符串。 */
    private static class NameSerializer<T extends Name> extends StdSerializer<T> {
        private static final long serialVersionUID = 1L;

        private final NameFormat format;

        private NameSerializer(Class<T> type, NameFormat format) {
            super(type);
            this.format = format;
        }

        @Override
        public void serialize(T value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(value.format(format));
        }
    }

    /** 校验 JSON 字符串并恢复已声明的名称类型。 */
    private static class NameDeserializer<T extends Name> extends StdDeserializer<T> {
        private static final long serialVersionUID = 1L;

        private final Class<T> type;

        private final Function<String, T> factory;

        private NameDeserializer(Class<T> type, Function<String, T> factory) {
            super(type);
            this.factory = factory;
            this.type = type;
        }

        @Override
        public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(VALUE_STRING)) {
                return type.cast(context.handleUnexpectedToken(handledType(), parser));
            }
            return factory.apply(parser.getText());
        }
    }
}
