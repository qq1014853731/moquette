package io.moquette.spring;

import cn.hutool.core.map.TableMap;
import io.moquette.BrokerConstants;
import io.moquette.interception.InterceptHandler;
import io.netty.handler.ssl.SslProvider;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/24 11:15
 */
@Data
@Accessors(chain = true)
public class BrokerProperties {

	/**
	 * jvm运行参数
	 *
	 * @see BrokerConstants#INTERCEPT_HANDLER_PROPERTY_NAME
	 */
	private String interceptHandler = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);

	/**
	 * 默认值 1
	 * @see BrokerConstants#BROKER_INTERCEPTOR_THREAD_POOL_SIZE
	 */
	private Integer interceptThreadPoolSize = 1;

	/**
	 * @see BrokerConstants#PERSISTENT_STORE_PROPERTY_NAME
	 */
    @Deprecated
	private String persistentStore;

    /**
     * @see BrokerConstants#PERSISTENCE_ENABLED_PROPERTY_NAME
     */
    private Boolean persistenceEnabled;

	/**
	 * @see BrokerConstants#AUTOSAVE_INTERVAL_PROPERTY_NAME
	 */
	private String autosaveInterval;

	/**
	 * @see BrokerConstants#PASSWORD_FILE_PROPERTY_NAME
	 */
	private String passwordFile;

	/**
	 * 默认 disabled 1833
	 * @see BrokerConstants#PORT_PROPERTY_NAME
	 * @see BrokerConstants#PORT
	 * @see BrokerConstants#DISABLED_PORT_BIND
	 */
	private Integer port;

	/**
	 * 默认 0.0.0.0
	 * @see BrokerConstants#HOST_PROPERTY_NAME
	 * @see BrokerConstants#HOST
	 */
	private String host = BrokerConstants.HOST;

	/**
	 * 无实现
	 *
	 * @see BrokerConstants#DEFAULT_MOQUETTE_STORE_H2_DB_FILENAME
	 */
	@Deprecated
	private String moquetteStoreH2;

	/**
	 * 无实现
	 *
	 * @see BrokerConstants#DEFAULT_PERSISTENT_PATH
	 */
	@Deprecated
	private String persistentPath;

	/**
	 * 默认 disabled 8080
	 * @see BrokerConstants#WEB_SOCKET_PORT_PROPERTY_NAME
	 * @see BrokerConstants#WEBSOCKET_PORT
	 * @see BrokerConstants#DISABLED_PORT_BIND
	 */
	private Integer websocketPort;

	/**
	 * @see BrokerConstants#WSS_PORT_PROPERTY_NAME
	 * @see BrokerConstants#DISABLED_PORT_BIND
	 */
	private Integer secureWebsocketPort;

	/**
	 * 默认值：/mqtt
	 * @see BrokerConstants#WEB_SOCKET_PATH_PROPERTY_NAME
	 * @see BrokerConstants#WEBSOCKET_PATH
	 */
	private String websocketPath = BrokerConstants.WEBSOCKET_PATH;

	/**
	 * @see BrokerConstants#WEB_SOCKET_MAX_FRAME_SIZE_PROPERTY_NAME
	 * 默认值：65536
	 */
	private String websocketMaxFrameSize;

	/**
	 * @see BrokerConstants#SESSION_QUEUE_SIZE
	 */
	private Integer sessionQueueSize;

	/**
	 * Defines the SSL implementation to use, default to "JDK".
	 * @see BrokerConstants#SSL_PROVIDER
	 * @see SslProvider#name()
	 */
	private SslProvider sslProvider;

	/**
	 * 默认disabled
	 * @see BrokerConstants#SSL_PORT_PROPERTY_NAME
	 * @see BrokerConstants#DISABLED_PORT_BIND
	 */
	private Integer sslPort;

	/**
	 * @see BrokerConstants#JKS_PATH_PROPERTY_NAME
	 */
	private String jksPath;

	/**
	 * for allowed types, default to "jks"
	 * @see BrokerConstants#KEY_STORE_TYPE
	 * @see java.security.KeyStore#getInstance(String)
	 */
	private String keyStoreType;

	/**
	 * @see BrokerConstants#KEY_STORE_PASSWORD_PROPERTY_NAME
	 */
	private String keyStorePassword;

	/**
	 * @see BrokerConstants#KEY_MANAGER_PASSWORD_PROPERTY_NAME
	 */
	private String keyManagerPassword;

	/**
	 * 默认 true
	 * @see BrokerConstants#ALLOW_ANONYMOUS_PROPERTY_NAME
	 */
	private Boolean allowAnonymous;

	/**
	 * 默认fasle
	 * @see BrokerConstants#REAUTHORIZE_SUBSCRIPTIONS_ON_CONNECT
	 */
	private Boolean reauthorizeSubscriptionsOnConnect;

	/**
	 * 默认false
	 * @see BrokerConstants#ALLOW_ZERO_BYTE_CLIENT_ID_PROPERTY_NAME
	 */
	private Boolean allowZeroByteClientId;

	/**
	 * @see BrokerConstants#ACL_FILE_PROPERTY_NAME
	 */
	private String aclFile;

	/**
	 * @see BrokerConstants#AUTHORIZATOR_CLASS_NAME
	 */
	private String authorizatorClass;

	/**
	 * @see BrokerConstants#AUTHENTICATOR_CLASS_NAME
	 */
	private String authenticatorClass;

	/**
	 * @see BrokerConstants#DB_AUTHENTICATOR_DRIVER
	 */
	private String authenticatorDbDriver;

	/**
	 * @see BrokerConstants#DB_AUTHENTICATOR_URL
	 */
	private String authenticatorDbUrl;

	/**
	 * @see BrokerConstants#DB_AUTHENTICATOR_QUERY
	 */
	private String authenticatorDbQuery;

	/**
	 * @see BrokerConstants#DB_AUTHENTICATOR_DIGEST
	 */
	private String authenticatorDbDigest;

	/**
	 * 默认false
	 * @see BrokerConstants#NEED_CLIENT_AUTH
	 */
	private String needClientAuth;

	/**
	 * 默认128
	 * @see BrokerConstants#NETTY_SO_BACKLOG_PROPERTY_NAME
	 */
	private Integer nettySoBacklog;

	/**
	 * 默认true
	 * @see BrokerConstants#NETTY_SO_REUSEADDR_PROPERTY_NAME
	 */
	private Boolean nettySoReuseaddr;

	/**
	 * 默认true
	 * @see BrokerConstants#NETTY_TCP_NODELAY_PROPERTY_NAME
	 */
	private Boolean nettyTcpNodelay;

	/**
	 * 默认true
	 * @see BrokerConstants#NETTY_SO_KEEPALIVE_PROPERTY_NAME
	 */
	private Boolean nettySoKeepalive;

	/**
	 * 默认10
	 * @see BrokerConstants#NETTY_CHANNEL_TIMEOUT_SECONDS_PROPERTY_NAME
	 */
	private Integer nettyChannelTimeoutSeconds;

	/**
	 * 默认false
	 * @see BrokerConstants#NETTY_EPOLL_PROPERTY_NAME
	 */
	private Boolean nettyEpoll;

	/**
	 * 默认8092
	 * @see BrokerConstants#NETTY_MAX_BYTES_PROPERTY_NAME
	 * @see BrokerConstants#DEFAULT_NETTY_MAX_BYTES_IN_MESSAGE
	 */
	private Integer nettyMqttMessageSize;

	/**
	 * 默认false
	 * @see BrokerConstants#IMMEDIATE_BUFFER_FLUSH_PROPERTY_NAME
	 */
	private Boolean immediateBufferFlush;

	/**
	 * 默认fasle
	 * @see BrokerConstants#METRICS_ENABLE_PROPERTY_NAME
	 */
	private Boolean useMetrics;

	/**
	 * @see BrokerConstants#METRICS_LIBRATO_EMAIL_PROPERTY_NAME
	 */
	private String metricsLibratoEmail;

	/**
	 * @see BrokerConstants#METRICS_LIBRATO_TOKEN_PROPERTY_NAME
	 */
	private String metricsLibratoToken;

	/**
	 * @see BrokerConstants#METRICS_LIBRATO_SOURCE_PROPERTY_NAME
	 */
	private String metricsLibratoSource;

	/**
	 * 默认 true
	 * @see BrokerConstants#ENABLE_TELEMETRY_NAME
	 */
	private Boolean telemetryEnabled = Boolean.TRUE;

	/**
	 * 默认 false
	 * @see BrokerConstants#BUGSNAG_ENABLE_PROPERTY_NAME
	 */
	private Boolean useBugsnag = Boolean.FALSE;

	/**
	 * @see BrokerConstants#BUGSNAG_TOKEN_PROPERTY_NAME
	 */
	private String bugsnagToken;

	/**
	 * 未定义
	 * @see BrokerConstants#STORAGE_CLASS_NAME
	 */
	@Deprecated
	private String storageClass;


	/**
	 * 与配置文件字段映射关系缓存
	 */
	@Getter
    @Setter(AccessLevel.NONE)
	public static final TableMap<String, String> FIELD_NAME_CACHE_MAP = new TableMap<String, String>(){
		{
			put("interceptHandler", "intercept.handler");
			put("interceptThreadPoolSize", "intercept.thread_pool.size");
			put("persistentStore", "persistent_store");
			put("persistenceEnabled", "persistence_enabled");
			put("autosaveInterval", "autosave_interval");
			put("passwordFile", "password_file");
			put("port", "port");
			put("host", "host");
			put("moquetteStoreH2", "moquette_store.h2");
			put("websocketPort", "websocket_port");
			put("secureWebsocketPort", "secure_websocket_port");
			put("websocketPath", "websocket_path");
			put("websocketMaxFrameSize", "websocket_max_frame_size");
			put("sessionQueueSize", "session_queue_size");
			put("sslProvider", "ssl_provider");
			put("sslPort", "ssl_port");
			put("jksPath", "jks_path");
			put("keyStoreType", "key_store_type");
			put("keyStorePassword", "key_store_password");
			put("keyManagerPassword", "key_manager_password");
			put("allowAnonymous", "allow_anonymous");
			put("reauthorizeSubscriptionsOnConnect", "reauthorize_subscriptions_on_connect");
			put("allowZeroByteClientId", "allow_zero_byte_client_id");
			put("aclFile", "acl_file");
			put("authorizatorClass", "authorizator_class");
			put("authenticatorClass", "authenticator_class");
			put("authenticatorDbDriver", "authenticator.db.driver");
			put("authenticatorDbUrl", "authenticator.db.url");
			put("authenticatorDbQuery", "authenticator.db.query");
			put("authenticatorDbDigest", "authenticator.db.digest");
			put("needClientAuth", "need_client_auth");
			put("nettySoBacklog", "netty.so_backlog");
			put("nettySoReuseaddr", "netty.so_reuseaddr");
			put("nettyTcpNodelay", "netty.tcp_nodelay");
			put("nettySoKeepalive", "netty.so_keepalive");
			put("nettyChannelTimeoutSeconds", "netty.channel_timeout.seconds");
			put("nettyEpoll", "netty.epoll");
			put("nettyMqttMessageSize", "netty.mqtt.message_size");
			put("immediateBufferFlush", "immediate_buffer_flush");
			put("useMetrics", "use_metrics");
			put("metricsLibratoEmail", "metrics.librato.email");
			put("metricsLibratoToken", "metrics.librato.token");
			put("metricsLibratoSource", "metrics.librato.source");
			put("telemetryEnabled", "telemetry_enabled");
			put("useBugsnag", "use_bugsnag");
			put("bugsnagToken", "bugsnag.token");
			put("storageClass", "storage_class");
		}
	};

    /**
     * custom intercept handlers
     */
    @Getter
    @Setter(AccessLevel.NONE)
    private List<InterceptHandler> interceptHandlers = new ArrayList<>();
    public BrokerProperties addInterceptHandler(InterceptHandler... interceptHandlers) {
        if (interceptHandlers == null || interceptHandlers.length == 0) {
            return this;
        }
        return addInterceptHandler(Arrays.asList(interceptHandlers));
    }

    public BrokerProperties addInterceptHandler(Collection<? extends InterceptHandler> interceptHandlers) {
        this.interceptHandlers.addAll(interceptHandlers);
        return this;
    }

}
