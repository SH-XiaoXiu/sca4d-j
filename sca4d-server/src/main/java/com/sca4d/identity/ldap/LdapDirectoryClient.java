package com.sca4d.identity.ldap;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.external.ExternalIdentityProfile;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.AuthenticationSource;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.DefaultTlsDirContextAuthenticationStrategy;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.SearchScope;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

/** 使用已保存 LDAP 配置执行服务账号搜索、用户 bind 和连接测试。 */
@Component
public class LdapDirectoryClient {

    private final SecretKeyring keyring;
    private final Sca4djIdentityProviderProperties properties;

    public LdapDirectoryClient(
            @NotNull SecretKeyring keyring,
            @NotNull Sca4djIdentityProviderProperties properties) {
        this.keyring = keyring;
        this.properties = properties;
    }

    /**
     * 搜索唯一目录用户并以该用户 DN 和请求密码执行 bind。
     *
     * @return bind 成功后可交给 ExternalIdentity 的目录资料
     */
    public @NotNull ExternalIdentityProfile authenticate(
            @NotNull LdapConnection connection,
            @NotNull String username,
            @NotNull String password) {
        if (username.isBlank() || password.isEmpty()) {
            throw new BizException(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID);
        }
        LdapContextSource source = contextSource(connection);
        LdapTemplate template = new LdapTemplate(source);
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        attributes.add(connection.getExternalSubjectAttribute());
        if (connection.getDisplayNameAttribute() != null) {
            attributes.add(connection.getDisplayNameAttribute());
        }
        if (connection.getEmailAttribute() != null) {
            attributes.add(connection.getEmailAttribute());
        }
        int readTimeout = timeoutMillis(properties.getReadTimeout(), "read-timeout");
        LdapQuery search = query()
                .base(connection.getUserSearchBase())
                .searchScope(SearchScope.SUBTREE)
                .countLimit(2)
                .timeLimit(readTimeout)
                .attributes(attributes.toArray(String[]::new))
                .filter(connection.getUserSearchFilter(), username);
        try {
            List<LdapDirectoryIdentity> matches = template.search(
                    search,
                    (ContextMapper<LdapDirectoryIdentity>) context -> {
                        DirContextOperations entry = (DirContextOperations) context;
                        String externalSubject = entry.getStringAttribute(
                                connection.getExternalSubjectAttribute());
                        String displayName = connection.getDisplayNameAttribute() == null
                                ? null
                                : entry.getStringAttribute(connection.getDisplayNameAttribute());
                        String email = connection.getEmailAttribute() == null
                                ? null
                                : entry.getStringAttribute(connection.getEmailAttribute());
                        return new LdapDirectoryIdentity(
                                entry.getNameInNamespace(),
                                new ExternalIdentityProfile(
                                        externalSubject,
                                        displayName,
                                        email,
                                        email != null && !email.isBlank(),
                                        null));
                    });
            if (matches.size() != 1) {
                throw new BizException(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID);
            }
            LdapDirectoryIdentity identity = matches.getFirst();
            DirContext userContext = null;
            try {
                userContext = source.getContext(identity.getAbsoluteDn(), password);
            } finally {
                LdapUtils.closeContext(userContext);
            }
            ExternalIdentityProfile profile = identity.getProfile();
            if (profile.getExternalSubject() == null || profile.getExternalSubject().isBlank()) {
                throw new BizException(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID);
            }
            return profile;
        } catch (BizException exception) {
            throw exception;
        } catch (AuthenticationException | IncorrectResultSizeDataAccessException exception) {
            throw new BizException(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID, exception);
        } catch (DataAccessException exception) {
            throw new BizException(IdentityProviderErrorCode.LDAP_UPSTREAM_FAILED, exception);
        }
    }

    /** 验证已保存的 TLS、服务账号和 base DN 可以建立只读连接。 */
    public void testConnection(@NotNull LdapConnection connection) {
        DirContext context = null;
        try {
            context = contextSource(connection).getReadOnlyContext();
            context.getAttributes("");
        } catch (javax.naming.AuthenticationException exception) {
            throw new BizException(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID, exception);
        } catch (NamingException | DataAccessException exception) {
            throw new BizException(IdentityProviderErrorCode.LDAP_UPSTREAM_FAILED, exception);
        } finally {
            LdapUtils.closeContext(context);
        }
    }

    /** @return 使用部署超时和配置 TLS 方式的非池化 ContextSource */
    private @NotNull LdapContextSource contextSource(@NotNull LdapConnection connection) {
        String servicePassword = connection.getEncryptedServiceAccountPassword() == null
                ? ""
                : keyring.decrypt(
                        "identity-provider.ldap."
                                + connection.getProviderId()
                                + ".service-account-password",
                        connection.getEncryptedServiceAccountPassword());
        String serviceDn = connection.getServiceAccountDn() == null
                ? ""
                : connection.getServiceAccountDn();
        LdapContextSource source = new LdapContextSource();
        source.setUrl(connection.getUrl());
        source.setBase(connection.getBaseDn());
        source.setPooled(false);
        source.setAuthenticationSource(new AuthenticationSource() {
            @Override
            public String getPrincipal() {
                return serviceDn;
            }

            @Override
            public String getCredentials() {
                return servicePassword;
            }
        });
        source.setBaseEnvironmentProperties(Map.of(
                "com.sun.jndi.ldap.connect.timeout",
                Integer.toString(timeoutMillis(properties.getConnectionTimeout(), "connection-timeout")),
                "com.sun.jndi.ldap.read.timeout",
                Integer.toString(timeoutMillis(properties.getReadTimeout(), "read-timeout")),
                Context.REFERRAL,
                "ignore"));
        if (connection.getTransportSecurity() == LdapTransportSecurity.STARTTLS) {
            source.setAuthenticationStrategy(new DefaultTlsDirContextAuthenticationStrategy());
        }
        source.afterPropertiesSet();
        return source;
    }

    /** @return JNDI 接受的正数毫秒超时 */
    private int timeoutMillis(Duration timeout, @NotNull String name) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("sca4dj.identity-provider." + name + " 必须是正数 Duration");
        }
        long millis = timeout.toMillis();
        if (millis > Integer.MAX_VALUE) {
            throw new IllegalStateException("sca4dj.identity-provider." + name + " 不能超过 2147483647ms");
        }
        return Math.max(1, (int) millis);
    }
}
