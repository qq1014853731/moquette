/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.moquette.broker;

import io.moquette.BrokerConstants;
import io.moquette.broker.config.IConfig;
import io.moquette.spring.core.AdvancedTlsX509KeyManager;
import io.moquette.spring.core.AdvancedTlsX509TrustManager;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Objects;

/**
 * Moquette integration implementation to load SSL certificate from local filesystem path configured in
 * config file.
 */
public class DefaultMoquetteSslContextCreator implements ISslContextCreator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMoquetteSslContextCreator.class);

    private final IConfig props;

    public DefaultMoquetteSslContextCreator(IConfig props) {
        this.props = Objects.requireNonNull(props);
    }

    @Override
    public SslContext initSSLContext() {
        LOG.info("Checking SSL configuration properties...");

        final String keyPassword = props.getProperty(BrokerConstants.KEY_MANAGER_PASSWORD_PROPERTY_NAME);
        if (keyPassword == null || keyPassword.isEmpty()) {
            LOG.warn("The key manager password is null or empty. The SSL context won't be initialized.");
            return null;
        }

        try {
            SslProvider sslProvider = getSslProvider();
            KeyStore ks = loadKeyStore();
            SslContextBuilder contextBuilder;
            switch (sslProvider) {
            case JDK:
                contextBuilder = builderWithJdkProvider(ks, keyPassword);
                break;
            case OPENSSL:
            case OPENSSL_REFCNT:
                contextBuilder = builderWithOpenSslProvider(ks, keyPassword);
                break;
            default:
                LOG.error("unsupported SSL provider {}", sslProvider);
                return null;
            }
            // if client authentification is enabled a trustmanager needs to be added to the ServerContext
            String sNeedsClientAuth = props.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false");
            if (Boolean.parseBoolean(sNeedsClientAuth)) {
                addClientAuthentication(ks, contextBuilder);
            }
            contextBuilder.sslProvider(sslProvider);
            SslContext sslContext = contextBuilder.build();
            LOG.info("The SSL context has been initialized successfully.");
            return sslContext;
        } catch (GeneralSecurityException | IOException ex) {
            LOG.error("Unable to initialize SSL context.", ex);
            return null;
        }
    }

    private KeyStore loadKeyStore() throws IOException, GeneralSecurityException {
        final String jksPath = props.getProperty(BrokerConstants.JKS_PATH_PROPERTY_NAME);
        LOG.info("Initializing SSL context. KeystorePath = {}.", jksPath);
        if (jksPath == null || jksPath.isEmpty()) {
            LOG.warn("The keystore path is null or empty. The SSL context won't be initialized.");
            return null;
        }
        final String keyStorePassword = props.getProperty(BrokerConstants.KEY_STORE_PASSWORD_PROPERTY_NAME);
        if (keyStorePassword == null || keyStorePassword.isEmpty()) {
            LOG.warn("The keystore password is null or empty. The SSL context won't be initialized.");
            return null;
        }
        String ksType = props.getProperty(BrokerConstants.KEY_STORE_TYPE, "jks");
        final KeyStore keyStore = KeyStore.getInstance(ksType);
        LOG.info("Loading keystore. KeystorePath = {}.", jksPath);
        try (InputStream jksInputStream = jksDatastore(jksPath)) {
            keyStore.load(jksInputStream, keyStorePassword.toCharArray());
        }
        return keyStore;
    }

    private static SslContextBuilder builderWithJdkProvider(KeyStore ks, String keyPassword)
            throws GeneralSecurityException {
        LOG.info("Initializing key manager...");
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPassword.toCharArray());
        LOG.info("Initializing SSL context...");
        return SslContextBuilder.forServer(kmf);
    }

    /**
     * The OpenSSL provider does not support the {@link KeyManagerFactory}, so we have to lookup the integration
     * certificate and key in order to provide it to OpenSSL.
     * <p>
     * TODO: SNI is currently not supported, we use only the first found private key.
     */
    private static SslContextBuilder builderWithOpenSslProvider(KeyStore ks, String keyPassword)
        throws GeneralSecurityException, IOException {
        return SslContextBuilder.forServer(new AdvancedTlsX509KeyManager(ks, keyPassword.toCharArray()));
    }

    private static void addClientAuthentication(KeyStore ks, SslContextBuilder contextBuilder)
        throws NoSuchAlgorithmException, KeyStoreException, CertificateException {
        LOG.warn("Client authentication is enabled. The keystore will be used as a truststore.");
        // use keystore as truststore, as integration needs to trust certificates signed by the integration certificates
        contextBuilder.clientAuth(ClientAuth.REQUIRE);
        AdvancedTlsX509TrustManager trustManager = AdvancedTlsX509TrustManager.newBuilder()
            .setVerification(AdvancedTlsX509TrustManager.Verification.InsecurelySkipAllVerification)
            .setSslSocketAndEnginePeerVerifier(null)
            .build();
        trustManager.updateTrustCredentials(ks);
        contextBuilder.trustManager(trustManager);
    }

    private SslProvider getSslProvider() {
        String providerName = props.getProperty(BrokerConstants.SSL_PROVIDER, SslProvider.JDK.name());
        try {
            return SslProvider.valueOf(providerName);
        } catch (IllegalArgumentException e) {
            LOG.warn("unknown SSL Provider {}, falling back on JDK provider", providerName);
            return SslProvider.JDK;
        }
    }

    private InputStream jksDatastore(String jksPath) throws IOException {
        URL jksUrl = getClass().getClassLoader().getResource(jksPath);
        if (jksUrl != null) {
            LOG.info("Starting with jks at {}, jks normal {}", jksUrl.toExternalForm(), jksUrl);
            return getClass().getClassLoader().getResourceAsStream(jksPath);
        }
        LOG.warn("No keystore has been found in the bundled resources. Scanning filesystem...");
        File jksFile = new File(jksPath);
        if (jksFile.exists()) {
            LOG.info("Loading external keystore. Url = {}.", jksFile.getAbsolutePath());
            return Files.newInputStream(jksFile.toPath());
        }
        throw new FileNotFoundException("The keystore file does not exist. Url = " + jksFile.getAbsolutePath());
    }
}
