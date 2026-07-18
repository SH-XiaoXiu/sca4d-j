package com.sca4d.identity.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.external.ExternalIdentityProfile;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.SelfSignedCertificateGenerator;
import com.unboundid.util.ObjectPair;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** 使用标准内存 LDAP 服务验证 StartTLS、LDAPS、服务账号搜索、用户 bind 和属性映射。 */
@ResourceLock("javax.net.ssl.trustStore")
class LdapDirectoryClientTest {

    private static InMemoryDirectoryServer directory;
    private static File keyStore;
    private static File trustStore;
    private static char[] keyStorePassword;
    private static String directoryHost;
    private static String previousTrustStore;
    private static String previousTrustStorePassword;
    private static String previousTrustStoreType;
    private static SSLContext previousDefaultSslContext;

    @BeforeAll
    static void startDirectory() throws Exception {
        ObjectPair<File, char[]> certificate = SelfSignedCertificateGenerator
                .generateTemporarySelfSignedCertificate("localhost", "JKS");
        keyStore = certificate.getFirst();
        keyStorePassword = certificate.getSecond();
        KeyStore generated = KeyStore.getInstance("JKS");
        try (FileInputStream input = new FileInputStream(keyStore)) {
            generated.load(input, keyStorePassword);
        }
        String alias = generated.aliases().nextElement();
        X509Certificate serverCertificate = (X509Certificate) generated.getCertificate(alias);
        String certificateHost = serverCertificate.getSubjectAlternativeNames().stream()
                .filter(name -> Integer.valueOf(7).equals(name.getFirst()))
                .map(name -> name.get(1).toString())
                .filter(name -> !name.contains(":"))
                .findFirst()
                .orElseThrow();
        InetAddress listenAddress = InetAddress.getByName(certificateHost);
        directoryHost = certificateHost;
        KeyStore trusted = KeyStore.getInstance("JKS");
        trusted.load(null, keyStorePassword);
        trusted.setCertificateEntry("directory", generated.getCertificate(alias));
        trustStore = Files.createTempFile("sca4d-ldap-trust", ".jks").toFile();
        try (FileOutputStream output = new FileOutputStream(trustStore)) {
            trusted.store(output, keyStorePassword);
        }
        previousDefaultSslContext = SSLContext.getDefault();
        previousTrustStore = System.getProperty("javax.net.ssl.trustStore");
        previousTrustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
        previousTrustStoreType = System.getProperty("javax.net.ssl.trustStoreType");
        System.setProperty("javax.net.ssl.trustStore", trustStore.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", new String(keyStorePassword));
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trusted);
        SSLContext testSslContext = SSLContext.getInstance("TLS");
        testSslContext.init(null, trustManagers.getTrustManagers(), null);
        SSLContext.setDefault(testSslContext);

        SSLUtil serverSsl = new SSLUtil(
                new KeyStoreKeyManager(keyStore, keyStorePassword, "JKS", null),
                new TrustAllTrustManager());
        SSLUtil clientSsl = new SSLUtil(new TrustAllTrustManager());
        InMemoryDirectoryServerConfig configuration = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        configuration.setSchema(null);
        configuration.setListenerConfigs(
                InMemoryListenerConfig.createLDAPConfig(
                        "starttls",
                        listenAddress,
                        0,
                        serverSsl.createSSLSocketFactory()),
                InMemoryListenerConfig.createLDAPSConfig(
                        "ldaps",
                        listenAddress,
                        0,
                        serverSsl.createSSLServerSocketFactory(),
                        clientSsl.createSSLSocketFactory()));
        configuration.addAdditionalBindCredentials(
                "cn=reader,dc=example,dc=com", "reader-password");
        configuration.addAdditionalBindCredentials(
                "uid=alice,ou=people,dc=example,dc=com", "user-password");
        directory = new InMemoryDirectoryServer(configuration);
        directory.startListening();
        directory.add(
                "dn: dc=example,dc=com",
                "objectClass: top",
                "objectClass: domain",
                "dc: example");
        directory.add(
                "dn: ou=people,dc=example,dc=com",
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: people");
        directory.add(
                "dn: cn=reader,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "cn: reader",
                "sn: reader");
        directory.add(
                "dn: uid=alice,ou=people,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: inetOrgPerson",
                "uid: alice",
                "cn: Alice",
                "sn: User",
                "displayName: Alice Directory",
                "mail: alice@example.com",
                "entryUUID: external-1");
    }

    @AfterAll
    static void stopDirectory() {
        if (directory != null) {
            directory.shutDown(true);
        }
        restore("javax.net.ssl.trustStore", previousTrustStore);
        restore("javax.net.ssl.trustStorePassword", previousTrustStorePassword);
        restore("javax.net.ssl.trustStoreType", previousTrustStoreType);
        if (previousDefaultSslContext != null) {
            SSLContext.setDefault(previousDefaultSslContext);
        }
        if (keyStore != null) {
            keyStore.delete();
        }
        if (trustStore != null) {
            trustStore.delete();
        }
    }

    @Test
    void shouldAuthenticateThroughStartTlsAndMapDirectoryProfile() {
        ExternalIdentityProfile profile = client().authenticate(
                connection(
                        "ldap://" + directoryHost + ":" + directory.getListenPort("starttls"),
                        LdapTransportSecurity.STARTTLS),
                "alice",
                "user-password");

        assertThat(profile.getExternalSubject()).isEqualTo("external-1");
        assertThat(profile.getDisplayName()).isEqualTo("Alice Directory");
        assertThat(profile.getEmail()).isEqualTo("alice@example.com");
        assertThat(profile.getEmailVerified()).isTrue();
    }

    @Test
    void shouldConnectThroughLdapsAndRejectWrongUserPassword() {
        LdapDirectoryClient client = client();
        LdapConnection connection = connection(
                "ldaps://" + directoryHost + ":" + directory.getListenPort("ldaps"),
                LdapTransportSecurity.LDAPS);

        client.testConnection(connection);
        assertThatThrownBy(() -> client.authenticate(connection, "alice", "wrong-password"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID));
    }

    /** @return 使用固定服务账号密码和测试超时的目录客户端。 */
    private LdapDirectoryClient client() {
        SecretKeyring keyring = mock(SecretKeyring.class);
        when(keyring.decrypt(
                eq("identity-provider.ldap.100.service-account-password"),
                any(EncryptedSecret.class)))
                .thenReturn("reader-password");
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.setConnectionTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(3));
        return new LdapDirectoryClient(keyring, properties);
    }

    /** @return 指向测试监听器的服务账号搜索配置。 */
    private LdapConnection connection(String url, LdapTransportSecurity security) {
        return new LdapConnection(
                100L,
                url,
                "dc=example,dc=com",
                "cn=reader,dc=example,dc=com",
                new EncryptedSecret("current", new byte[12], new byte[] {1}),
                "ou=people",
                "(&(objectClass=person)(uid={0}))",
                "entryUUID",
                "displayName",
                "mail",
                security,
                3L);
    }

    /** 恢复测试前的 JVM 系统属性。 */
    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
