package com.sca4d.app.server.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.codec.JsonCodec;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginConfiguration;
import com.sca4d.core.api.plugin.PluginConfigurationSnapshot;
import com.sca4d.core.api.plugin.PluginDescriptor;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.spring.setting.JarSettingReader;
import com.sca4d.core.spring.setting.YamlCodec;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Objects;

/** 读取插件作者声明的 `plugin.yml`，映射 Server 元信息并提供每个代次的公开配置。 */
@Component
public final class Sca4dServerPluginConfiguration implements PluginConfiguration<Sca4dServerPluginSettings> {
    private static final Logger LOG = LoggerFactory.getLogger(Sca4dServerPluginConfiguration.class);

    private static final TreeCode METADATA = TreeCode.of("PLUGIN:METADATA");

    private static final SettingOwner OWNER = new SettingOwner(TreeCode.of("PLUGIN:ARTIFACT"), "metadata");

    private final Environment environment;

    private final YamlCodec<PluginDescriptor> metadata = new YamlCodec<>(new DescriptorCodec());

    public Sca4dServerPluginConfiguration(@NotNull Environment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    @Override
    public @NotNull Class<Sca4dServerPluginSettings> type() {
        return Sca4dServerPluginSettings.class;
    }

    @Override
    public @NotNull PluginConfigurationSnapshot<Sca4dServerPluginSettings> load(@NotNull Path artifact) {
        // 元信息只从候选 JAR 读取，插件私有字段由插件自己的解析器解释。
        var document = new JarSettingReader(artifact, "plugin.yml", OWNER).read(METADATA, OWNER);
        if (!document.present()) {
            throw new IllegalArgumentException("Server 插件缺少 plugin.yml");
        }
        PluginDescriptor descriptor = metadata.decode(document.value());
        Sca4dServerPluginSettings values = new Sca4dServerPluginSettings(
                environment.getRequiredProperty("spring.application.name"));
        LOG.debug("Server 插件配置已读取 | pluginId={} api={}", descriptor.id(), descriptor.api());
        return new PluginConfigurationSnapshot<>(descriptor, values);
    }

    @Override
    public @NotNull PluginConfigurationSnapshot<Sca4dServerPluginSettings> reload(@NotNull Path artifact) {
        PluginConfigurationSnapshot<Sca4dServerPluginSettings> snapshot = load(artifact);
        LOG.debug("Server 插件配置已重载 | pluginId={}", snapshot.descriptor().id());
        return snapshot;
    }

    @Override
    public void unload(@NotNull PluginConfigurationSnapshot<Sca4dServerPluginSettings> snapshot) {
        // Server 公开配置只有不可变值，不持有需要关闭的资源。
    }

    /** 解释 Sca4d Server 认可的元信息键，其他字段保持由插件自行解释。 */
    private static final class DescriptorCodec implements JsonCodec<PluginDescriptor> {
        @Override
        public @NotNull JsonNode encode(@NotNull PluginDescriptor value) {
            var document = JsonNodeFactory.instance.objectNode();
            var plugin = document.putObject("plugin");
            plugin.put("name", value.id().format(NameFormat.COLON));
            plugin.put("version", value.version());
            plugin.put("main", value.entry());
            plugin.put("api", value.api().format(NameFormat.COLON));
            plugin.put("requires", value.requiresApi());
            return document;
        }

        @Override
        public @NotNull PluginDescriptor decode(@NotNull JsonNode wire) {
            JsonNode plugin = wire.path("plugin");
            if (!plugin.isObject()) {
                throw new IllegalArgumentException("Server 插件元信息必须包含 plugin 对象");
            }
            return new PluginDescriptor(
                    TreeCode.of(requiredText(plugin, "name")),
                    requiredText(plugin, "version"),
                    TreeCode.of(requiredText(plugin, "api")),
                    requiredText(plugin, "requires"),
                    requiredText(plugin, "main"));
        }

        /** 读取非空文本字段，不把数字或其他 YAML 值隐式转换成元信息。 */
        private static @NotNull String requiredText(@NotNull JsonNode plugin, @NotNull String key) {
            JsonNode value = plugin.path(key);
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw new IllegalArgumentException("Server 插件元信息缺少非空文本字段: " + key);
            }
            return value.textValue();
        }
    }
}
