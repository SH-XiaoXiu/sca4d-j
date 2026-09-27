package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.federation.FederatedProvider;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 向登录页或后台返回的不含 Secret Provider 投影。 */
@Schema(name = "FederatedProvider")
@SuppressFBWarnings(value = "SS_SHOULD_BE_STATIC", justification = "@Localize requires instance fields for BaseViewSerializer metadata")
@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class FederatedProviderView extends BaseView {
    private final long id;

    private final String code;

    private final String protocol;

    private final String status;

    private final boolean loginPageVisible;

    private final int displayOrder;

    @Localize(key = "name")
    private final @NotNull String name = "";

    @Localize(key = "buttonText")
    private final @NotNull String buttonText = "";

    private final String iconReference;

    private final @NotNull Version revision;

    private FederatedProviderView(FederatedProvider provider) {
        super(LocalizedFields.fromJson(provider.i18n()));
        this.id = provider.id();
        this.code = provider.code();
        this.protocol = provider.protocol().name();
        this.status = provider.status().name();
        this.loginPageVisible = provider.loginPageVisible();
        this.displayOrder = provider.displayOrder();
        this.iconReference = provider.iconReference();
        this.revision = provider.version();
    }

    /** @return Provider 安全投影 */
    public static @NotNull FederatedProviderView from(@NotNull FederatedProvider provider) {
        return new FederatedProviderView(Objects.requireNonNull(provider, "Provider 不能为空"));
    }

    /** @return Provider 数据库标识 */
    public long getId() {
        return id;
    }

    /** @return Provider 编码 */
    public String getCode() {
        return code;
    }

    /** @return Provider 使用的授权协议 */
    public String getProtocol() {
        return protocol;
    }

    /** @return Provider 当前状态 */
    public String getStatus() {
        return status;
    }

    /** @return 是否在登录页展示 */
    public boolean isLoginPageVisible() {
        return loginPageVisible;
    }

    /** @return 登录页展示顺序 */
    public int getDisplayOrder() {
        return displayOrder;
    }

    /** @return 当前请求 Locale 下的 Provider 名称 */
    public @NotNull String getName() {
        return name;
    }

    /** @return 当前请求 Locale 下的登录按钮文案 */
    public @NotNull String getButtonText() {
        return buttonText;
    }

    /** @return Provider 图标键或应用文件引用 */
    public String getIconReference() {
        return iconReference;
    }

    /** @return 当前 Provider 的乐观并发版本 */
    public long getVersion() {
        return revision.value();
    }

    /** @return 生成当前完整 Provider 表示的版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
