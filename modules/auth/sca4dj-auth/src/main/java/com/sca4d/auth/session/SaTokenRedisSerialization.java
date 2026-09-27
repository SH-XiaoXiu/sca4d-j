package com.sca4d.auth.session;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.util.SaFoxUtil;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 为官方 Sa-Token DAO 的独立 Redis 模板安装服务器 key 序列化，不修改 Token 或客户端协议。 */
public final class SaTokenRedisSerialization implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SaTokenRedisSerialization.class);

    /** 为官方 DAO 安装名称边界转换，不单独创建 Redis 连接。 */
    public SaTokenRedisSerialization() {
    }

    @Override
    public @NotNull Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) {
        if (bean instanceof SaTokenDaoForRedisTemplate dao) {
            dao.stringRedisTemplate.setKeySerializer(new KeySerializer());
            LOG.trace("Sa-Token Redis key 序列化已安装 | beanName={}", beanName);
            if (bean.getClass() == SaTokenDaoForRedisTemplate.class) {
                return new ResourceDao(dao.stringRedisTemplate);
            }
        }
        return bean;
    }

    /** 复用官方 DAO 的读写和 TTL 行为，搜索时按解码后的原始文本筛选再分页。 */
    private static final class ResourceDao extends SaTokenDaoForRedisTemplate {

        /** 保存官方 DAO 已初始化的模板，不创建第二套 Redis 连接。 */
        private ResourceDao(StringRedisTemplate template) {
            stringRedisTemplate = template;
            isInit = true;
        }

        @Override
        public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
            Set<String> candidates = Objects
                    .requireNonNull(stringRedisTemplate.keys(prefix + "*"), "Redis key 查询没有返回结果");
            List<String> matches = new ArrayList<>(
                    candidates.stream()
                            .filter(key -> key.startsWith(prefix) && key.substring(prefix.length()).contains(keyword))
                            .toList());
            return SaFoxUtil.searchList(matches, start, size, sortType);
        }
    }

    /** 在字符串协议适配处转成 TreeCode，搜索模式保留官方 DAO 的星号匹配语义。 */
    private static final class KeySerializer implements RedisSerializer<String> {
        private static final TreeCode ROOT = TreeCode.ofColon("AUTH:SA_TOKEN");

        @Override
        public byte @Nullable [] serialize(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String encoded;
            if (value.indexOf('*') < 0) {
                encoded = Key.of(ROOT, value).format(NameFormat.COLON);
            } else {
                String pattern = Arrays.stream(value.split("\\*", -1))
                        .map(part -> HexFormat.of().withUpperCase().formatHex(part.getBytes(StandardCharsets.UTF_8)))
                        .collect(Collectors.joining("*"));
                encoded = Key.of(ROOT).format(NameFormat.COLON) + ":V_" + pattern;
            }
            return encoded.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public @Nullable String deserialize(byte @Nullable [] bytes) {
            if (bytes == null) {
                return null;
            }
            Key key = Key.parse(new String(bytes, StandardCharsets.UTF_8), NameFormat.COLON);
            if (!key.isUnder(ROOT) || key.segments().size() != ROOT.size() + 1) {
                throw new IllegalArgumentException("Redis key 不属于 Sa-Token 资源");
            }
            String dynamic = key.segments().get(ROOT.size());
            if (!dynamic.startsWith("V_")) {
                throw new IllegalArgumentException("Sa-Token key 必须使用动态文本");
            }
            return new String(HexFormat.of().parseHex(dynamic.substring(2)), StandardCharsets.UTF_8);
        }
    }
}
