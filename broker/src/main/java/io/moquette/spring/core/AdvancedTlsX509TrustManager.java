package io.moquette.spring.core;

import io.moquette.spring.CertificateUtils;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class AdvancedTlsX509TrustManager extends X509ExtendedTrustManager {

    private final Verification verification;
    private final SslSocketAndEnginePeerVerifier socketAndEnginePeerVerifier;

    // The delegated trust manager used to perform traditional certificate verification.
    private volatile X509ExtendedTrustManager delegateManager = null;

    private AdvancedTlsX509TrustManager(Verification verification,
                                        SslSocketAndEnginePeerVerifier socketAndEnginePeerVerifier) {
        this.verification = verification;
        this.socketAndEnginePeerVerifier = socketAndEnginePeerVerifier;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        throw new CertificateException(
            "Not enough information to validate peer. SSLEngine or Socket required.");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException {
        checkTrusted(chain, authType, null, socket, false);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException {
        checkTrusted(chain, authType, engine, null, false);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException {
        checkTrusted(chain, authType, engine, null, true);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        throw new CertificateException(
            "Not enough information to validate peer. SSLEngine or Socket required.");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException {
        checkTrusted(chain, authType, null, socket, true);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (this.delegateManager == null) {
            return new X509Certificate[0];
        }
        return this.delegateManager.getAcceptedIssuers();
    }

    /**
     * Uses the default trust certificates stored on user's local system.
     * After this is used, functions that will provide new credential
     * data(e.g. updateTrustCredentials(), updateTrustCredentialsFromFile()) should not be called.
     */
    public void useSystemDefaultTrustCerts() throws CertificateException, KeyStoreException,
        NoSuchAlgorithmException {
        // Passing a null value of KeyStore would make {@code TrustManagerFactory} attempt to use
        // system-default trust CA certs.
        this.delegateManager = createDelegateTrustManager(null);
    }

    /**
     * Updates the current cached trust certificates as well as the key store.
     *
     * @param trustCerts the trust certificates that are going to be used
     */
    public void updateTrustCredentials(X509Certificate[] trustCerts) throws CertificateException,
        KeyStoreException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        int i = 1;
        for (X509Certificate cert : trustCerts) {
            String alias = Integer.toString(i);
            keyStore.setCertificateEntry(alias, cert);
            i++;
        }
        this.delegateManager = createDelegateTrustManager(keyStore);
    }

    public void updateTrustCredentials(KeyStore keyStore) throws CertificateException,
        KeyStoreException, NoSuchAlgorithmException {
        this.delegateManager = createDelegateTrustManager(keyStore);
    }

    private static X509ExtendedTrustManager createDelegateTrustManager(KeyStore keyStore)
        throws CertificateException, KeyStoreException, NoSuchAlgorithmException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        X509ExtendedTrustManager delegateManager = null;
        TrustManager[] tms = tmf.getTrustManagers();
        // Iterate over the returned trust managers, looking for an instance of X509TrustManager.
        // If found, use that as the delegate trust manager.
        for (TrustManager tm : tms) {
            if (tm instanceof X509ExtendedTrustManager) {
                delegateManager = (X509ExtendedTrustManager) tm;
                break;
            }
        }
        if (delegateManager == null) {
            throw new CertificateException(
                "Failed to find X509ExtendedTrustManager with default TrustManager algorithm "
                    + TrustManagerFactory.getDefaultAlgorithm());
        }
        return delegateManager;
    }

    private void checkTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine,
                              Socket socket, boolean checkingServer) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new IllegalArgumentException(
                "Want certificate verification but got null or empty certificates");
        }
        if (sslEngine == null && socket == null) {
            throw new CertificateException(
                "Not enough information to validate peer. SSLEngine or Socket required.");
        }
        if (this.verification != Verification.InsecurelySkipAllVerification) {
            X509ExtendedTrustManager currentDelegateManager = this.delegateManager;
            if (currentDelegateManager == null) {
                throw new CertificateException("No trust roots configured");
            }
            if (checkingServer) {
                String algorithm = this.verification == Verification.CertificateAndHostNameVerification ? "HTTPS" : "";
                if (sslEngine != null) {
                    SSLParameters sslParams = sslEngine.getSSLParameters();
                    sslParams.setEndpointIdentificationAlgorithm(algorithm);
                    sslEngine.setSSLParameters(sslParams);
                    currentDelegateManager.checkServerTrusted(chain, authType, sslEngine);
                } else {
                    if (!(socket instanceof SSLSocket)) {
                        throw new CertificateException("socket is not a type of SSLSocket");
                    }
                    SSLSocket sslSocket = (SSLSocket) socket;
                    SSLParameters sslParams = sslSocket.getSSLParameters();
                    sslParams.setEndpointIdentificationAlgorithm(algorithm);
                    sslSocket.setSSLParameters(sslParams);
                    currentDelegateManager.checkServerTrusted(chain, authType, sslSocket);
                }
            } else {
                currentDelegateManager.checkClientTrusted(chain, authType, sslEngine);
            }
        }
        // Perform the additional peer cert check.
        if (socketAndEnginePeerVerifier != null) {
            if (sslEngine != null) {
                socketAndEnginePeerVerifier.verifyPeerCertificate(chain, authType, sslEngine);
            } else {
                socketAndEnginePeerVerifier.verifyPeerCertificate(chain, authType, socket);
            }
        }
    }

    /**
     * Schedules a {@code ScheduledExecutorService} to read trust certificates from a local file path
     * periodically, and update the cached trust certs if there is an update.
     *
     * @param trustCertFile the file on disk holding the trust certificates
     * @param period        the period between successive read-and-update executions
     * @param unit          the time unit of the initialDelay and period parameters
     * @param executor      the execute service we use to read and update the credentials
     * @return an object that caller should close when the file refreshes are not needed
     */
    public Closeable updateTrustCredentialsFromFile(File trustCertFile, long period, TimeUnit unit,
                                                    ScheduledExecutorService executor) {
        LoadFilePathExecution loadFilePathExecution = new LoadFilePathExecution(trustCertFile);
        final ScheduledFuture<?> future = executor.scheduleWithFixedDelay(loadFilePathExecution, 0, period, unit);
        return () -> future.cancel(false);
    }

    private class LoadFilePathExecution implements Runnable {
        File file;
        long currentTime;

        public LoadFilePathExecution(File file) {
            this.file = file;
            this.currentTime = 0;
        }

        @Override
        public void run() {
            try {
                this.currentTime = readAndUpdate(this.file, this.currentTime);
            } catch (CertificateException | IOException | KeyStoreException
                     | NoSuchAlgorithmException e) {
                log.error("Failed refreshing trust CAs from file. Using previous CAs", e);
            }
        }
    }

    /**
     * Reads the trust certificates specified in the path location, and update the key store if the
     * modified time has changed since last read.
     *
     * @param trustCertFile the file on disk holding the trust certificates
     * @param oldTime       the time when the trust file is modified during last execution
     * @return oldTime if failed or the modified time is not changed, otherwise the new modified time
     */
    private long readAndUpdate(File trustCertFile, long oldTime)
        throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
        long newTime = trustCertFile.lastModified();
        if (newTime == oldTime) {
            return oldTime;
        }
        FileInputStream inputStream = new FileInputStream(trustCertFile);
        try {
            X509Certificate[] certificates = CertificateUtils.getX509Certificates(inputStream);
            updateTrustCredentials(certificates);
            return newTime;
        } finally {
            inputStream.close();
        }
    }

    // Mainly used to avoid throwing IO Exceptions in java.io.Closeable.
    public interface Closeable extends java.io.Closeable {
        @Override
        public void close();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    // The verification mode when authenticating the peer certificate.
    public enum Verification {
        // This is the DEFAULT and RECOMMENDED mode for most applications.
        // Setting this on the client side will do the certificate and hostname verification, while
        // setting this on the server side will only do the certificate verification.
        CertificateAndHostNameVerification,
        // This SHOULD be chosen only when you know what the implication this will bring, and have a
        // basic understanding about TLS.
        // It SHOULD be accompanied with proper additional peer identity checks set through
        // {@code PeerVerifier}(nit: why this @code not working?). Failing to do so will leave
        // applications to MITM attack.
        // Also note that this will only take effect if the underlying SDK implementation invokes
        // checkClientTrusted/checkServerTrusted with the {@code SSLEngine} parameter while doing
        // verification.
        // Setting this on either side will only do the certificate verification.
        CertificateOnlyVerification,
        // Setting is very DANGEROUS. Please try to avoid this in a real production environment, unless
        // you are a super advanced user intended to re-implement the whole verification logic on your
        // own. A secure verification might include:
        // 1. proper verification on the peer certificate chain
        // 2. proper checks on the identity of the peer certificate
        InsecurelySkipAllVerification,
    }

    // Additional custom peer verification check.
    // It will be used when checkClientTrusted/checkServerTrusted is called with the {@code Socket} or
    // the {@code SSLEngine} parameter.
    public interface SslSocketAndEnginePeerVerifier {
        /**
         * Verifies the peer certificate chain. For more information, please refer to
         * {@code X509ExtendedTrustManager}.
         *
         * @param peerCertChain the certificate chain sent from the peer
         * @param authType      the key exchange algorithm used, e.g. "RSA", "DHE_DSS", etc
         * @param socket        the socket used for this connection. This parameter can be null, which
         *                      indicates that implementations need not check the ssl parameters
         */
        void verifyPeerCertificate(X509Certificate[] peerCertChain, String authType, Socket socket)
            throws CertificateException;

        /**
         * Verifies the peer certificate chain. For more information, please refer to
         * {@code X509ExtendedTrustManager}.
         *
         * @param peerCertChain the certificate chain sent from the peer
         * @param authType      the key exchange algorithm used, e.g. "RSA", "DHE_DSS", etc
         * @param engine        the engine used for this connection. This parameter can be null, which
         *                      indicates that implementations need not check the ssl parameters
         */
        void verifyPeerCertificate(X509Certificate[] peerCertChain, String authType, SSLEngine engine)
            throws CertificateException;
    }

    public static final class Builder {

        private Verification verification = Verification.CertificateAndHostNameVerification;
        private SslSocketAndEnginePeerVerifier socketAndEnginePeerVerifier;

        private Builder() {
        }

        public Builder setVerification(Verification verification) {
            this.verification = verification;
            return this;
        }

        public Builder setSslSocketAndEnginePeerVerifier(SslSocketAndEnginePeerVerifier verifier) {
            this.socketAndEnginePeerVerifier = verifier;
            return this;
        }

        public AdvancedTlsX509TrustManager build() {
            return new AdvancedTlsX509TrustManager(this.verification, this.socketAndEnginePeerVerifier);
        }
    }
}
