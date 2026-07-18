package com.sca4d.common.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证 BaseView 在最终 JSON 中追加展示字段且不改变普通字段。 */
class BaseViewTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(
            new SimpleModule("test-base-view").setSerializerModifier(new BaseViewSerializerModifier()));
    private final DemoView view = new DemoView(
            1L, "Alice", "hidden", List.of(new RoleView("admin", "管理员"), new RoleView("user", "用户")));

    @Test
    void shouldKeepNormalBeanFields() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(view));

        assertThat(json.get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("name").asText()).isEqualTo("Alice");
    }

    @Test
    void shouldApplyViewJsonActions() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(view));

        assertThat(json.has("secret")).isFalse();
        assertThat(json.get("displayName").asText()).isEqualTo("Alice");
        assertThat(json.get("profile").get("phone").asText()).isEqualTo("138****8888");
        assertThat(json.get("profile").get("summary").get("roleCount").asInt()).isEqualTo(2);
        assertThat(json.get("roles").get(0).has("internalCode")).isFalse();
        assertThat(json.get("roles").get(0).get("label").asText()).isEqualTo("管理员");
    }

    @Test
    void dataOpsShouldSupportViewDataTransform() {
        String value = DataOps.text(" Demo ").trim().lower().get();
        var lengths = DataOps.seq(List.of("a", "bb")).map(String::length).list();

        assertThat(value).isEqualTo("demo");
        assertThat(lengths).containsExactly(1, 2);
    }

    /** 验证普通字段和 DataOps 最终 JSON 修改的测试视图。 */
    private static final class DemoView extends BaseView {

        private final long id;
        private final String name;
        private final String secret;
        private final List<RoleView> roles;

        /**
         * 创建保留普通 Bean 字段并支持最终 JSON 操作的测试视图。
         *
         * @param id 测试视图主键
         * @param name 展示名称
         * @param secret 应在最终 JSON 中移除的敏感字段
         * @param roles 待裁剪内部编码的角色视图
         */
        private DemoView(
                long id,
                @NotNull String name,
                @NotNull String secret,
                @NotNull List<RoleView> roles) {
            this.id = id;
            this.name = name;
            this.secret = secret;
            this.roles = roles;
            json(json -> json.put("displayName", name)
                    .put("profile.phone", "138****8888")
                    .put("profile.summary.roleCount", roles.size())
                    .remove("secret")
                    .list("roles", role -> role.remove("internalCode")));
        }

        public long getId() {
            return id;
        }

        public @NotNull String getName() {
            return name;
        }

        public @NotNull String getSecret() {
            return secret;
        }

        public @NotNull List<RoleView> getRoles() {
            return roles;
        }
    }

    /** 保存角色编码和名称的嵌套测试视图。 */
    private static final class RoleView {

        private final String internalCode;
        private final String label;

        /**
         * 创建包含内部编码和公开标签的角色测试视图。
         *
         * @param internalCode 最终 JSON 应移除的内部编码
         * @param label 对外展示标签
         */
        private RoleView(@NotNull String internalCode, @NotNull String label) {
            this.internalCode = internalCode;
            this.label = label;
        }

        public @NotNull String getInternalCode() {
            return internalCode;
        }

        public @NotNull String getLabel() {
            return label;
        }
    }
}
