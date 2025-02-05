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
import io.moquette.broker.config.*;
import io.moquette.broker.security.*;
import io.moquette.broker.subscriptions.CTrieSubscriptionDirectory;
import io.moquette.broker.subscriptions.ISubscriptionsDirectory;
import io.moquette.broker.unsafequeues.QueueException;
import io.moquette.interception.BrokerInterceptor;
import io.moquette.interception.InterceptHandler;
import io.moquette.persistence.H2Builder;
import io.moquette.persistence.MemorySubscriptionsRepository;
import io.moquette.persistence.RedisSubscriptionRepository;
import io.moquette.persistence.SegmentQueueRepository;
import io.moquette.spring.core.MqttServerAutoConfiguration;
import io.moquette.spring.support.RedisRepositorySupport;
import io.moquette.spring.support.RepositorySupport;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.moquette.logging.LoggingUtils.getInterceptorIds;

@Slf4j
public class Server {

    public static final String MOQUETTE_VERSION = "0.17-SNAPSHOT";

    private ScheduledExecutorService scheduler;
    private NewNettyAcceptor acceptor;
    private volatile boolean initialized;
    private PostOffice dispatcher;
    private BrokerInterceptor interceptor;
    private H2Builder h2Builder;
    private SessionRegistry sessions;
    private boolean standalone = false;

    public static void main(String[] args) throws IOException {
        final Server server = new Server();
        try {
            server.startStandaloneServer();
        } catch (RuntimeException e) {
            System.exit(1);
        }
        System.out.println("Server started, version " + MOQUETTE_VERSION);
        //Bind a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer));
    }

    /**
     * Starts Moquette bringing the configuration from the file located at m_config/moquette.conf
     *
     * @throws IOException in case of any IO error.
     */
    public void startServer() throws IOException {
        File defaultConfigurationFile = defaultConfigFile();
        log.info("Starting Moquette integration. Configuration file path={}", defaultConfigurationFile.getAbsolutePath());
        IResourceLoader filesystemLoader = new FileResourceLoader(defaultConfigurationFile);
        final IConfig config = new ResourceLoaderConfig(filesystemLoader);
        startServer(config);
    }

    private void startStandaloneServer() throws IOException {
        this.standalone = true;
        startServer();
    }

    private static File defaultConfigFile() {
        String configPath = System.getProperty("moquette.path", null);
        return new File(configPath, IConfig.DEFAULT_CONFIG);
    }

    /**
     * Starts Moquette bringing the configuration from the given file
     *
     * @param configFile text file that contains the configuration.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(File configFile) throws IOException {
        log.info("Starting Moquette integration. Configuration file path: {}", configFile.getAbsolutePath());
        IResourceLoader filesystemLoader = new FileResourceLoader(configFile);
        final IConfig config = new ResourceLoaderConfig(filesystemLoader);
        startServer(config);
    }

    /**
     * Starts the integration with the given properties.
     * <p>
     * Its suggested to at least have the following properties:
     * <ul>
     *  <li>port</li>
     *  <li>password_file</li>
     * </ul>
     *
     * @param configProps the properties map to use as configuration.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(Properties configProps) throws IOException {
        log.debug("Starting Moquette integration using properties object");
        final IConfig config = new MemoryConfig(configProps);
        startServer(config);
    }

    /**
     * Starts Moquette bringing the configuration files from the given Config implementation.
     *
     * @param config the configuration to use to start the broker.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(IConfig config) throws IOException {
        log.debug("Starting Moquette integration using IConfig instance");
        startServer(config, null);
    }

    /**
     * Starts Moquette with config provided by an implementation of IConfig class and with the set
     * of InterceptHandler.
     *
     * @param config   the configuration to use to start the broker.
     * @param handlers the handlers to install in the broker.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(IConfig config, List<? extends InterceptHandler> handlers) throws IOException {
        log.debug("Starting moquette integration using IConfig instance and intercept handlers");
        startServer(config, handlers, null, null, null);
    }

    public void startServer(IConfig config, List<? extends InterceptHandler> handlers, ISslContextCreator sslCtxCreator,
                            IAuthenticator authenticator, IAuthorizatorPolicy authorizatorPolicy) throws IOException {
        final long start = System.currentTimeMillis();
        if (handlers == null) {
            handlers = Collections.emptyList();
        }
        log.trace("Starting Moquette Server. MQTT message interceptors={}", getInterceptorIds(handlers));

        scheduler = Executors.newScheduledThreadPool(1);

        final String handlerProp = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
        if (handlerProp != null) {
            config.setProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME, handlerProp);
        }
        initInterceptors(config, handlers);
        log.debug("Initialized MQTT protocol processor");
        if (sslCtxCreator == null) {
            log.info("Using default SSL context creator");
            sslCtxCreator = new DefaultMoquetteSslContextCreator(config);
        }
        authenticator = initializeAuthenticator(authenticator, config);
        authorizatorPolicy = initializeAuthorizatorPolicy(authorizatorPolicy, config);

        final ISubscriptionsRepository subscriptionsRepository;
        final IQueueRepository queueRepository;
        final IRetainedRepository retainedRepository;

        if (config.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME) != null) {
            log.warn("Using a deprecated setting {} please update to {}",
                BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME, BrokerConstants.DATA_PATH_PROPERTY_NAME);
            log.warn("Forcing {} to true", BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME);
            config.setProperty(BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME, Boolean.TRUE.toString());

            final String persistencePath = config.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME);
            final String dataPath = persistencePath.substring(0, persistencePath.lastIndexOf("/"));
            log.warn("Forcing {} to {}", BrokerConstants.DATA_PATH_PROPERTY_NAME, dataPath);
            config.setProperty(BrokerConstants.DATA_PATH_PROPERTY_NAME, dataPath);
        }

        if (Boolean.parseBoolean(config.getProperty(BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME, Boolean.FALSE.toString()))) {
            final Path dataPath = Paths.get(config.getProperty(BrokerConstants.DATA_PATH_PROPERTY_NAME));
            if (!dataPath.toFile().exists()) {
                if (dataPath.toFile().mkdirs()) {
                    log.debug("Created data_path {} folder", dataPath);
                } else {
                    log.warn("Impossible to create the data_path {}", dataPath);
                }
            }

            log.debug("Configuring persistent subscriptions store and queues, path: {}", dataPath);
            final int autosaveInterval = Integer.parseInt(config.getProperty(BrokerConstants.AUTOSAVE_INTERVAL_PROPERTY_NAME, "30"));
            h2Builder = new H2Builder(scheduler, dataPath, autosaveInterval).initStore();
            queueRepository = initQueuesRepository(config, dataPath, h2Builder);
            log.trace("Configuring H2 subscriptions repository");
            subscriptionsRepository = h2Builder.subscriptionsRepository();
            retainedRepository = h2Builder.retainedRepository();
        } else {
            RepositorySupport repositorySupport = null;
            try {
                repositorySupport = MqttServerAutoConfiguration.applicationContext.getBean(RepositorySupport.class);
            } catch (Exception ignore) {}
            if (repositorySupport == null) {
                // load default repository
                log.trace("Configuring in-memory subscriptions store");
                subscriptionsRepository = new MemorySubscriptionsRepository();
                queueRepository = new MemoryQueueRepository();
                retainedRepository = new MemoryRetainedRepository();
            } else if (repositorySupport instanceof RedisRepositorySupport) {
                // load redis repository
                RedisRepositorySupport redisRepositorySupport = (RedisRepositorySupport) repositorySupport;
                subscriptionsRepository = new RedisSubscriptionRepository(redisRepositorySupport);
                queueRepository = new RedisQueueRepository(redisRepositorySupport);
                retainedRepository = new RedisRetainedRepository(redisRepositorySupport);
            } else {
                // TODO load another repository. eg: mybatis and spring-cache and more....
                throw new RemoteException("unknow repository support type!");
            }

        }

        ISubscriptionsDirectory subscriptions = new CTrieSubscriptionDirectory();
        subscriptions.init(subscriptionsRepository);
        final Authorizator authorizator = new Authorizator(authorizatorPolicy);
        sessions = new SessionRegistry(subscriptions, queueRepository, authorizator);
        final int sessionQueueSize = config.intProp(BrokerConstants.SESSION_QUEUE_SIZE, 1024);
        dispatcher = new PostOffice(subscriptions, retainedRepository, sessions, interceptor, authorizator,
                                    sessionQueueSize);
        final BrokerConfiguration brokerConfig = new BrokerConfiguration(config);
        MQTTConnectionFactory connectionFactory = new MQTTConnectionFactory(brokerConfig, authenticator, sessions,
                                                                            dispatcher);

        final NewNettyMQTTHandler mqttHandler = new NewNettyMQTTHandler(connectionFactory);
        acceptor = new NewNettyAcceptor();
        acceptor.initialize(mqttHandler, config, sslCtxCreator);

        final long startTime = System.currentTimeMillis() - start;
        log.info("Moquette integration has been started successfully in {} ms", startTime);

        if (config.boolProp(BrokerConstants.ENABLE_TELEMETRY_NAME, true)) {
            collectAndSendTelemetryDataAsynch(config);
        }

        initialized = true;
    }

    private static IQueueRepository initQueuesRepository(IConfig config, Path dataPath, H2Builder h2Builder) throws IOException {
        final IQueueRepository queueRepository;
        final String queueType = config.getProperty(BrokerConstants.PERSISTENT_QUEUE_TYPE_PROPERTY_NAME);
        if ("h2".equalsIgnoreCase(queueType)) {
            log.info("Configuring H2 queue store");
            queueRepository = h2Builder.queueRepository();
        } else if ("segmented".equalsIgnoreCase(queueType)) {
            log.info("Configuring segmented queue store to {}", dataPath);
            final int pageSize = config.intProp(BrokerConstants.SEGMENTED_QUEUE_PAGE_SIZE, BrokerConstants.DEFAULT_SEGMENTED_QUEUE_PAGE_SIZE);
            final int segmentSize = config.intProp(BrokerConstants.SEGMENTED_QUEUE_SEGMENT_SIZE, BrokerConstants.DEFAULT_SEGMENTED_QUEUE_SEGMENT_SIZE);
            try {
                queueRepository = new SegmentQueueRepository(dataPath, pageSize, segmentSize);
            } catch (QueueException e) {
                throw new IOException("Problem in configuring persistent queue on path " + dataPath, e);
            }
        } else {
            final String errMsg = String.format("Invalid property for %s found [%s] while only h2 or segmented are admitted", BrokerConstants.PERSISTENT_QUEUE_TYPE_PROPERTY_NAME, queueType);
            throw new RuntimeException(errMsg);
        }
        return queueRepository;
    }

    private void collectAndSendTelemetryDataAsynch(IConfig config) {
        final Thread telCollector = new Thread(() -> collectAndSendTelemetryData(config));
        telCollector.start();
    }

    private void collectAndSendTelemetryData(IConfig config) {
        final String uuid = checkOrCreateUUID(config);

        final String telemetryDoc = collectTelemetryData(uuid);

        try {
            sendTelemetryData(telemetryDoc);
        } catch (IOException e) {
            log.info("Can't reach the telemetry collector");
            if (log.isDebugEnabled()) {
                log.debug("Original exception", e);
            }
        }
    }

    private String checkOrCreateUUID(IConfig config) {
        final String storagePath = config.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME, "");
        final Path uuidFilePath = Paths.get(storagePath, ".moquette_uuid");
        if (Files.exists(uuidFilePath)) {
            try {
                return new String(Files.readAllBytes(uuidFilePath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Problem accessing file path: {}", uuidFilePath, e);
            }
        }
        final UUID uuid = UUID.randomUUID();
        final FileWriter f;
        try {
            f = new FileWriter(uuidFilePath.toFile(), false);
            f.write(uuid.toString());
            f.close();
        } catch (IOException e) {
            log.error("Problem writing new UUID to file path: {}", uuidFilePath, e);
        }

        return uuid.toString();
    }

    /**
     * @return a json string with the content of max mem, jvm version and similar telemetry data.
     * @param uuid*/
    private String collectTelemetryData(String uuid) {
        final String remoteIp = retrievePublicIP();
        final String os = System.getProperty("os.name");
        final String cpuArch = System.getProperty("os.arch");
        final String jvmVersion = System.getProperty("java.specification.version");
        final String jvmVendor = System.getProperty("java.vendor");
        final long maxMemory = Runtime.getRuntime().maxMemory();
        final String maxHeap = maxMemory == Long.MAX_VALUE ? "undefined" : Long.toString(maxMemory);

        return String.format(
            "{\"os\": \"%s\", " +
                "\"cpu_arch\": \"%s\", " +
                "\"jvm_version\": \"%s\", " +
                "\"jvm_vendor\": \"%s\", " +
                "\"broker_version\": \"%s\", " +
                "\"standalone\": %s," +
                "\"max_heap\": \"%s\", " +
                "\"remote_ip\": \"%s\", " +
                "\"uuid\": \"%s\"}",
            os, cpuArch, jvmVersion, jvmVendor, MOQUETTE_VERSION, this.standalone, maxHeap, remoteIp, uuid);
    }

    private String retrievePublicIP() {
        try {
            URL url = new URL("http://whatismyip.akamai.com");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            final int status = con.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                log.debug("What's my IP service replied with {}", status);
                return "";
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            return in.readLine();
        } catch (Exception e) {
            log.debug("Can't connect to what's my IP service");
            return "";
        }
    }

    private void sendTelemetryData(String telemetryDoc) throws IOException {
        URL url = new URL("https://telemetry.moquette.io/api/v1/notify");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setInstanceFollowRedirects(true);

        // POST
        con.setDoOutput(true);
        final byte[] input = telemetryDoc.getBytes("utf-8");
        try (OutputStream os = con.getOutputStream()) {
            os.write(input, 0, input.length);
        }

        int status = con.getResponseCode();
        log.trace("Response code is {}", status);

        boolean redirect = false;

        // normally, 3xx is redirect
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER)
                redirect = true;
        }

        log.trace("Response Code: {} ", status);

        if (redirect) {

            // get redirect url from "location" header field
            String newUrl = con.getHeaderField("Location");

            // open the new connnection again
            con = (HttpURLConnection) new URL(newUrl).openConnection();
            con.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
            con.addRequestProperty("User-Agent", "Mozilla");
            con.addRequestProperty("Referer", "google.com");
            con.setRequestMethod("POST");

            // POST
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                os.write(input, 0, input.length);
            }

            log.trace("Redirect to URL: {}", newUrl);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        log.trace("Content: {}", content);

        con.disconnect();
    }

    private IAuthorizatorPolicy initializeAuthorizatorPolicy(IAuthorizatorPolicy authorizatorPolicy, IConfig props) {
        log.debug("Configuring MQTT authorizator policy");
        String authorizatorClassName = props.getProperty(BrokerConstants.AUTHORIZATOR_CLASS_NAME, "");
        if (authorizatorPolicy == null && !authorizatorClassName.isEmpty()) {
            authorizatorPolicy = loadClass(authorizatorClassName, IAuthorizatorPolicy.class, IConfig.class, props);
        }

        if (authorizatorPolicy == null) {
            String aclFilePath = props.getProperty(BrokerConstants.ACL_FILE_PROPERTY_NAME, "");
            if (aclFilePath != null && !aclFilePath.isEmpty()) {
                authorizatorPolicy = new DenyAllAuthorizatorPolicy();
                try {
                    log.info("Parsing ACL file. Path = {}", aclFilePath);
                    IResourceLoader resourceLoader = props.getResourceLoader();
                    authorizatorPolicy = ACLFileParser.parse(resourceLoader.loadResource(aclFilePath));
                } catch (ParseException pex) {
                    log.error("Unable to parse ACL file. path = {}", aclFilePath, pex);
                }
            } else {
                authorizatorPolicy = new PermitAllAuthorizatorPolicy();
            }
            log.info("Authorizator policy {} instance will be used", authorizatorPolicy.getClass().getName());
        }
        return authorizatorPolicy;
    }

    private IAuthenticator initializeAuthenticator(IAuthenticator authenticator, IConfig props) {
        log.debug("Configuring MQTT authenticator");
        String authenticatorClassName = props.getProperty(BrokerConstants.AUTHENTICATOR_CLASS_NAME, "");

        if (authenticator == null && !authenticatorClassName.isEmpty()) {
            authenticator = loadClass(authenticatorClassName, IAuthenticator.class, IConfig.class, props);
        }

        IResourceLoader resourceLoader = props.getResourceLoader();
        if (authenticator == null) {
            String passwdPath = props.getProperty(BrokerConstants.PASSWORD_FILE_PROPERTY_NAME, "");
            if (passwdPath.isEmpty()) {
                authenticator = new AcceptAllAuthenticator();
            } else {
                authenticator = new ResourceAuthenticator(resourceLoader, passwdPath);
            }
            log.info("An {} authenticator instance will be used", authenticator.getClass().getName());
        }
        return authenticator;
    }

    private void initInterceptors(IConfig props, List<? extends InterceptHandler> embeddedObservers) {
        log.info("Configuring message interceptors...");

        List<InterceptHandler> observers = new ArrayList<>(embeddedObservers);
        String interceptorClassName = props.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
        if (interceptorClassName != null && !interceptorClassName.isEmpty()) {
            InterceptHandler handler = loadClass(interceptorClassName, InterceptHandler.class,
                                                 io.moquette.broker.Server.class, this);
            if (handler != null) {
                observers.add(handler);
            }
        }
        interceptor = new BrokerInterceptor(props, observers);
    }

    @SuppressWarnings("unchecked")
    private <T, U> T loadClass(String className, Class<T> intrface, Class<U> constructorArgClass, U props) {
        T instance = null;
        try {
            // check if constructor with constructor arg class parameter
            // exists
            log.info("Invoking constructor with {} argument. ClassName={}, interfaceName={}",
                     constructorArgClass.getName(), className, intrface.getName());
            instance = this.getClass().getClassLoader()
                .loadClass(className)
                .asSubclass(intrface)
                .getConstructor(constructorArgClass)
                .newInstance(props);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
            log.warn("Unable to invoke constructor with {} argument. ClassName={}, interfaceName={}, cause={}, " +
                     "errorMessage={}", constructorArgClass.getName(), className, intrface.getName(), ex.getCause(),
                     ex.getMessage());
            return null;
        } catch (NoSuchMethodException | InvocationTargetException e) {
            try {
                log.info("Invoking default constructor. ClassName={}, interfaceName={}", className, intrface.getName());
                // fallback to default constructor
                instance = this.getClass().getClassLoader()
                    .loadClass(className)
                    .asSubclass(intrface)
                    .getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException |
                NoSuchMethodException | InvocationTargetException ex) {
                log.error("Unable to invoke default constructor. ClassName={}, interfaceName={}, cause={}, " +
                          "errorMessage={}", className, intrface.getName(), ex.getCause(), ex.getMessage());
                return null;
            }
        }

        return instance;
    }

    /**
     * Use the broker to publish a message. It's intended for embedding applications. It can be used
     * only after the integration is correctly started with startServer.
     *
     * @param msg      the message to forward. The ByteBuf in the message will be released.
     * @param clientId the id of the sending integration.
     * @throws IllegalStateException if the integration is not yet started
     * @return
     */
    public RoutingResults internalPublish(MqttPublishMessage msg, final String clientId) {
        final int messageID = msg.variableHeader().packetId();
        if (!initialized) {
            log.error("Moquette is not started, internal message cannot be published. CId: {}, messageId: {}", clientId,
                      messageID);
            throw new IllegalStateException("Can't publish on a integration is not yet started");
        }
        log.trace("Internal publishing message CId: {}, messageId: {}", clientId, messageID);
        final RoutingResults routingResults = dispatcher.internalPublish(msg);
        msg.payload().release();
        return routingResults;
    }

    public void stopServer() {
        log.info("Unbinding integration from the configured ports");
        if (acceptor == null) {
            log.error("Closing a badly started server, exit immediately");
            return;
        }
        acceptor.close();
        log.trace("Stopping MQTT protocol processor");
        initialized = false;

        // calling shutdown() does not actually stop tasks that are not cancelled,
        // and SessionsRepository does not stop its tasks. Thus shutdownNow().
        scheduler.shutdownNow();

        sessions.close();

        if (h2Builder != null) {
            log.trace("Shutting down H2 persistence");
            h2Builder.closeStore();
        }

        interceptor.stop();
        dispatcher.terminate();
        log.info("Moquette integration has been stopped.");
    }

    public int getPort() {
        return acceptor.getPort();
    }

    public int getSslPort() {
        return acceptor.getSslPort();
    }

    /**
     * SPI method used by Broker embedded applications to get list of subscribers. Returns null if
     * the broker is not started.
     *
     * @return list of subscriptions.
     */
// TODO reimplement this
//    public List<Subscription> getSubscriptions() {
//        if (m_processorBootstrapper == null) {
//            return null;
//        }
//        return this.subscriptionsStore.listAllSubscriptions();
//    }

    /**
     * SPI method used by Broker embedded applications to add intercept handlers.
     *
     * @param interceptHandler the handler to add.
     */
    public void addInterceptHandler(InterceptHandler interceptHandler) {
        if (!initialized) {
            log.error("Moquette is not started, MQTT message interceptor cannot be added. InterceptorId={}",
                interceptHandler.getID());
            throw new IllegalStateException("Can't register interceptors on a integration that is not yet started");
        }
        log.info("Adding MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        interceptor.addInterceptHandler(interceptHandler);
    }

    /**
     * SPI method used by Broker embedded applications to remove intercept handlers.
     *
     * @param interceptHandler the handler to remove.
     */
    public void removeInterceptHandler(InterceptHandler interceptHandler) {
        if (!initialized) {
            log.error("Moquette is not started, MQTT message interceptor cannot be removed. InterceptorId={}",
                interceptHandler.getID());
            throw new IllegalStateException("Can't deregister interceptors from a integration that is not yet started");
        }
        log.info("Removing MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        interceptor.removeInterceptHandler(interceptHandler);
    }

    /**
     * Return a list of descriptors of connected clients.
     * */
    public Collection<ClientDescriptor> listConnectedClients() {
        return sessions.listConnectedClients();
    }
}
