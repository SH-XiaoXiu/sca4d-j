package com.sca4d.core.api.notification;

import com.sca4d.core.api.message.MessageContent;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 保存一次渠道无关通知的编码、内部接收人、语言和模板变量。
 */
public class Notification {

    private static final int MAXIMUM_VARIABLES = 100;

    private static final int MAXIMUM_VARIABLE_VALUE_LENGTH = 4096;

    private static final Pattern VARIABLE = Pattern.compile("[a-z][a-zA-Z0-9_]{0,63}");

    private final @NotNull TreeCode code;

    private final @NotNull NotificationRecipient recipient;

    private final @NotNull Locale locale;

    private final @NotNull Map<@NotNull String, @NotNull String> variables;

    private final @NotNull List<@NotNull MessageContent> contents;

    private final @Nullable Region region;

    private final @NotNull Set<@NotNull TreeCode> channels;

    private final @NotNull Set<@NotNull TreeCode> requiredChannels;

    /**
     * 创建已经由 `NotificationType` 回调解析并冻结路由事实的通知。
     */
    public Notification(
            @NotNull TreeCode code,
            @NotNull NotificationRecipient recipient,
            @NotNull Locale locale,
            @NotNull Map<@NotNull String, @NotNull String> variables,
            @Nullable Region region,
            @NotNull Set<@NotNull TreeCode> channels) {
        this(
                code,
                recipient,
                locale,
                variables,
                List.of(),
                region,
                channels,
                Set.of());
    }

    /**
     * 创建已经冻结内容和渠道要求的通知。
     */
    public Notification(
            @NotNull TreeCode code,
            @NotNull NotificationRecipient recipient,
            @NotNull Locale locale,
            @NotNull Map<@NotNull String, @NotNull String> variables,
            @NotNull List<? extends @NotNull MessageContent> contents,
            @Nullable Region region,
            @NotNull Set<@NotNull TreeCode> channels,
            @NotNull Set<@NotNull TreeCode> requiredChannels) {
        TreeCode checkedCode = Objects.requireNonNull(code, "通知编码不能为空");
        if (checkedCode.hasNode(TreeCode.ofSegment("*"))) {
            throw new IllegalArgumentException("通知编码不能包含通配符节点");
        }
        this.code = checkedCode;
        this.recipient = Objects.requireNonNull(recipient, "通知接收人不能为空");
        Locale checkedLocale = Locale.forLanguageTag(
                Objects.requireNonNull(locale, "通知 Locale 不能为空").toLanguageTag());
        if (checkedLocale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("通知 Locale 必须包含语言");
        }
        this.locale = checkedLocale;
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        Map<String, String> checkedVariables = Objects.requireNonNull(variables, "通知变量不能为空");
        if (checkedVariables.size() > MAXIMUM_VARIABLES) {
            throw new IllegalArgumentException("通知变量不能超过 " + MAXIMUM_VARIABLES + " 项");
        }
        checkedVariables.forEach((name, value) -> {
            String checkedName = requireVariableName(name);
            String checkedValue = Objects.requireNonNull(value, "通知变量值不能为空");
            if (checkedValue.length() > MAXIMUM_VARIABLE_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "通知变量值不能超过 " + MAXIMUM_VARIABLE_VALUE_LENGTH + " 个字符: " + checkedName);
            }
            copied.put(checkedName, checkedValue);
        });
        this.variables = Map.copyOf(copied);
        this.contents = List.copyOf(Objects.requireNonNull(contents, "通知内容不能为空"));
        this.region = region;
        Set<TreeCode> checkedChannels = Set.copyOf(
                Objects.requireNonNull(channels, "通知渠道不能为空"));
        if (checkedChannels.isEmpty()) {
            throw new IllegalArgumentException("通知至少需要一个投递渠道");
        }
        Set<TreeCode> checkedRequiredChannels = Set.copyOf(
                Objects.requireNonNull(requiredChannels, "必发通知渠道不能为空"));
        if (!checkedChannels.containsAll(checkedRequiredChannels)) {
            throw new IllegalArgumentException("必发渠道必须属于通知渠道");
        }
        this.channels = checkedChannels;
        this.requiredChannels = checkedRequiredChannels;
    }

    /**
     * 校验并返回统一模板使用的稳定变量名。
     */
    static @NotNull String requireVariableName(@NotNull String name) {
        String checkedName = Objects.requireNonNull(name, "通知变量名不能为空");
        if (!VARIABLE.matcher(checkedName).matches()) {
            throw new IllegalArgumentException("通知变量名格式不正确: " + checkedName);
        }
        return checkedName;
    }

    /**
     * @return 通知稳定编码
     */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * @return 内部接收人
     */
    @Contract(pure = true)
    public @NotNull NotificationRecipient recipient() {
        return recipient;
    }

    /**
     * @return 调用方选择的 Locale
     */
    @Contract(pure = true)
    public @NotNull Locale locale() {
        return locale;
    }

    /**
     * @return 不可修改模板变量
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Map<@NotNull String, @NotNull String> variables() {
        return variables;
    }

    /**
     * @return 本次通知携带的不可修改消息内容
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable List<@NotNull MessageContent> contents() {
        return contents;
    }

    /**
     * @return 具体应用解析的业务区域；没有区域语义时为空
     */
    @Contract(pure = true)
    public @Nullable Region region() {
        return region;
    }

    /**
     * @return 入队前已经冻结的不可修改渠道集合
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Set<@NotNull TreeCode> channels() {
        return channels;
    }

    /**
     * @return 本次投递中必须成功的渠道
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Set<@NotNull TreeCode> requiredChannels() {
        return requiredChannels;
    }

    @Override
    public @NotNull String toString() {
        return "Notification[code=" + code + ", recipientType=" + recipient.type()
                + ", recipientId=" + recipient.id() + ", locale=" + locale.toLanguageTag()
                + ", region=" + region + ", channels=" + channels + ", contentCount=" + contents.size()
                + ", variableNames=" + variables.keySet() + ']';
    }
}
