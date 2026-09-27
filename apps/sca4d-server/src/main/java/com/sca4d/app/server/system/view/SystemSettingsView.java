package com.sca4d.app.server.system.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.app.server.system.ManagedSetting;
import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.setting.SystemSetting;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按分组返回管理端可以查看和修改的全部 Settings。 */
@SuppressWarnings("unused")
public final class SystemSettingsView {

    private final List<Group> groups;

    private SystemSettingsView(@NotNull List<Group> groups) {
        this.groups = List.copyOf(groups);
    }

    /** 从设置管理结果创建分组 HTTP 投影。 */
    @CheckReturnValue
    public static @NotNull SystemSettingsView from(@NotNull List<@NotNull ManagedSetting> settings) {
        Map<String, List<ManagedSetting>> grouped = new LinkedHashMap<>();
        for (ManagedSetting setting : Objects.requireNonNull(settings, "设置管理结果不能为空")) {
            grouped.computeIfAbsent(setting.setting().group().key(), key -> new ArrayList<>())
                    .add(setting);
        }
        List<Group> groups = grouped.values()
                .stream()
                .map(Group::from)
                .toList();
        return new SystemSettingsView(groups);
    }

    /** @return 按展示顺序排列的设置分组 */
    public @NotNull @Unmodifiable List<@NotNull Group> getGroups() {
        return groups;
    }

    /** 管理端中的一个设置分组。 */
    public static final class Group extends BaseView {
        private final String key;

        private final String labelKey;

        @Localize(source = "labelText")
        private final String label;

        private final int sortOrder;

        private final List<Item> settings;

        private Group(
                @NotNull String key,
                @NotNull String labelKey,
                int sortOrder,
                @NotNull List<Item> settings) {
            this.key = key;
            this.labelKey = labelKey;
            this.label = labelKey;
            this.sortOrder = sortOrder;
            this.settings = List.copyOf(settings);
        }

        private static @NotNull Group from(@NotNull List<ManagedSetting> settings) {
            ManagedSetting first = settings.getFirst();
            return new Group(
                    first.setting().group().key(),
                    first.setting().group().labelKey(),
                    first.setting().group().sortOrder(),
                    settings.stream().map(Item::new).toList());
        }

        /** @return 设置分组键 */
        public @NotNull String getKey() {
            return key;
        }

        /** @return 设置分组名称消息键 */
        public @NotNull String getLabelKey() {
            return labelKey;
        }

        /** @return 当前请求语言的设置分组名称 */
        public @NotNull String getLabel() {
            return label;
        }

        /** @return 设置分组展示顺序 */
        public int getSortOrder() {
            return sortOrder;
        }

        /** @return 当前分组中的设置 */
        public @NotNull @Unmodifiable List<@NotNull Item> getSettings() {
            return settings;
        }

        /** @return 分组名称的可本地化文本 */
        @SuppressWarnings("ProtectedMemberInFinalClass") // @Localize 要求 source 是 protected 方法。
        protected @NotNull LocalizableText labelText() {
            return MessageText.of(labelKey);
        }
    }

    /** 管理端中的一个类型化设置。 */
    public static final class Item extends BaseView {
        private final String key;

        private final String labelKey;

        @Localize(source = "labelText")
        private final String label;

        private final String descriptionKey;

        @Localize(source = "descriptionText")
        private final String description;

        private final String valueType;

        private final JsonNode value;

        private final JsonNode defaultValue;

        private final boolean stored;

        private final boolean secret;

        private final boolean readOnly;

        private final int sortOrder;

        private final long version;

        private Item(@NotNull ManagedSetting managed) {
            SystemSetting<?> setting = managed.setting();
            this.key = setting.key();
            this.labelKey = setting.labelKey();
            this.label = setting.labelKey();
            this.descriptionKey = setting.descriptionKey();
            this.description = setting.descriptionKey();
            this.valueType = setting.codec().valueType();
            this.value = managed.value();
            this.defaultValue = setting.secret() ? null : encodeDefault(setting);
            this.stored = managed.stored();
            this.secret = setting.secret();
            this.readOnly = managed.synchronizedFromEnvironment();
            this.sortOrder = setting.sortOrder();
            this.version = managed.version().value();
        }

        /** @return 设置键 */
        public @NotNull String getKey() {
            return key;
        }

        /** @return 设置名称消息键 */
        public @NotNull String getLabelKey() {
            return labelKey;
        }

        /** @return 当前请求语言的设置名称 */
        public @NotNull String getLabel() {
            return label;
        }

        /** @return 设置说明消息键 */
        public @NotNull String getDescriptionKey() {
            return descriptionKey;
        }

        /** @return 当前请求语言的设置说明 */
        public @NotNull String getDescription() {
            return description;
        }

        /** @return 管理端输入控件使用的稳定值类型 */
        public @NotNull String getValueType() {
            return valueType;
        }

        /** @return 非敏感设置的当前编码值；敏感设置返回空 */
        public @Nullable JsonNode getValue() {
            return value;
        }

        /** @return 非敏感设置的 Java 默认编码值；敏感设置返回空 */
        public @Nullable JsonNode getDefaultValue() {
            return defaultValue;
        }

        /** @return 数据库中存在设置值时返回 {@code true} */
        public boolean isStored() {
            return stored;
        }

        /** @return 设置值必须加密且不返回当前值时返回 {@code true} */
        public boolean isSecret() {
            return secret;
        }

        /** @return 设置由初始化节点持续同步时返回 {@code true} */
        public boolean isReadOnly() {
            return readOnly;
        }

        /** @return 分组内展示顺序 */
        public int getSortOrder() {
            return sortOrder;
        }

        /** @return 当前设置行版本；数据库没有该设置行时返回 {@code 0} */
        public long getVersion() {
            return version;
        }

        /** @return 设置名称的可本地化文本 */
        @SuppressWarnings("ProtectedMemberInFinalClass") // @Localize 要求 source 是 protected 方法。
        protected @NotNull LocalizableText labelText() {
            return MessageText.of(labelKey);
        }

        /** @return 设置说明的可本地化文本 */
        @SuppressWarnings("ProtectedMemberInFinalClass") // @Localize 要求 source 是 protected 方法。
        protected @NotNull LocalizableText descriptionText() {
            return MessageText.of(descriptionKey);
        }

        /** 捕获设置默认值的泛型并编码。 */
        private static <T> @NotNull JsonNode encodeDefault(@NotNull SystemSetting<T> setting) {
            return setting.codec().encodeJson(setting.defaultValue());
        }
    }
}
