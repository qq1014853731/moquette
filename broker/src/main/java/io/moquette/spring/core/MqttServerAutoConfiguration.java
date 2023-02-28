package io.moquette.spring.core;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.system.SystemUtil;
import io.moquette.broker.DefaultMoquetteSslContextCreator;
import io.moquette.broker.ISslContextCreator;
import io.moquette.broker.Server;
import io.moquette.broker.config.ClasspathResourceLoader;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.broker.config.ResourceLoaderConfig;
import io.moquette.interception.InterceptHandler;
import io.moquette.spring.BrokerProperties;
import io.moquette.spring.ReloadableSslContext;
import io.netty.handler.ssl.SslContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/24 09:33
 */
@Slf4j
@RequiredArgsConstructor
public class MqttServerAutoConfiguration {


	/**
	 * 系统配置环境变量前缀，后缀为配置文件的key，可以动态配置
	 */
	private static final String SYSTEM_ENV_PREFIX = "mqtt.";


	/**
	 * @return 默认配置文件配置
	 */
	@Bean(name = "mqttDefaultConfig")
	@ConditionalOnMissingBean(IConfig.class)
	public IConfig mqttDefaultConfig() {
		ClasspathResourceLoader classpathResourceLoader = new ClasspathResourceLoader(IConfig.DEFAULT_CONFIG);
		return new ResourceLoaderConfig(classpathResourceLoader);
	}

	/**
	 * @param mqttDefaultConfig 原有配置文件配置
	 * @param mqttConfigurationCustomizers 自定义配置方法
	 * @return 自定义配置
	 */
	@Bean
	@ConditionalOnBean(value = {IConfig.class}, name = "mqttDefaultConfig")
	public BrokerProperties brokerProperties(IConfig mqttDefaultConfig, List<MqttConfigurationCustomizer> mqttConfigurationCustomizers) {
		// config转properties
		Field[] fields = ReflectUtil.getFields(mqttDefaultConfig.getClass(), field -> field.getType() == Properties.class);
		Assert.notEmpty(fields, "BrokerProperties无法获取properties属性");
		Field propertiesField = fields[0];
		Properties properties = (Properties) ReflectUtil.getFieldValue(mqttDefaultConfig, propertiesField);

		BrokerProperties brokerProperties = new BrokerProperties();
		Collection<String> values = BrokerProperties.FIELD_NAME_CACHE_MAP.values();
		for (String configKey : values) {
			Object o = properties.get(configKey);
			if (o == null) {
				// 配置文件取不到就去系统环境取，取系统环境变量时需要前缀：mqtt.
				o = SystemUtil.get(SYSTEM_ENV_PREFIX + configKey);
			}
			if (o == null) {
				continue;
			}
			String configValue = String.valueOf(o);
			String fieldName = BrokerProperties.getFIELD_NAME_CACHE_MAP().getKey(configKey);
			Field field = ReflectUtil.getField(BrokerProperties.class, fieldName);
			if (field != null) {
				ReflectUtil.setFieldValue(brokerProperties, fieldName, Convert.convertQuietly(field.getType(), configValue));
			}
		}

		for (MqttConfigurationCustomizer mqttConfigurationCustomizer : mqttConfigurationCustomizers) {
			mqttConfigurationCustomizer.customize(brokerProperties);
		}
		return brokerProperties;
	}

	/**
	 * MqttBrokerServer 实例
	 * @param brokerProperties 配置bean
	 * @param interceptHandlers 拦截器
	 * @return 实例Bean
	 */
	@Bean
	@ConditionalOnMissingBean(Server.class)
	@ConditionalOnBean(BrokerProperties.class)
	public Server server(BrokerProperties brokerProperties,
						 List<InterceptHandler> interceptHandlers,
                         ReloadableSslContext reloadableSslContext) throws IOException {
		Properties properties = new Properties();
		Set<String> fieldNames = BrokerProperties.FIELD_NAME_CACHE_MAP.keySet();
		for (String fieldName : fieldNames) {
			String configKey = BrokerProperties.FIELD_NAME_CACHE_MAP.get(fieldName);
			Object configValue = ReflectUtil.getFieldValue(brokerProperties, fieldName);
			if (configValue != null) {
				properties.setProperty(configKey, String.valueOf(configValue));
			}
		}
		IConfig config = new MemoryConfig(properties);
		Server server = new Server();
		server.startServer(config, interceptHandlers, () -> reloadableSslContext, null, null);
		printSuccess(brokerProperties);
		Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer));
		return server;
	}

    @Bean
    @ConditionalOnMissingBean(ISslContextCreator.class)
    @ConditionalOnBean(IConfig.class)
    public ReloadableSslContext sslContextCreator(IConfig props) {
        SslContext sslContext = new DefaultMoquetteSslContextCreator(props).initSSLContext();
        return new ReloadableSslContext(sslContext);
    }

	public void printSuccess(BrokerProperties brokerProperties) {
		Integer port = brokerProperties.getPort();
		if (port != null) {
			log.info(">>>>  MOQUETTE {} 启动成功, MQTT端口：{}", Server.MOQUETTE_VERSION, port);
		}
		Integer sslPort = brokerProperties.getSslPort();
		if (sslPort != null) {
			log.info(">>>>  MOQUETTE {} 启动成功, MQTT-SSL 端口：{}", Server.MOQUETTE_VERSION, sslPort);
		}
		Integer websocketPort = brokerProperties.getWebsocketPort();
		if (websocketPort != null) {
			String websocketPath = brokerProperties.getWebsocketPath();
			log.info(">>>>  MOQUETTE {} 启动成功, WebSocket-MQTT 端口：{} 地址：{}", Server.MOQUETTE_VERSION, websocketPort, websocketPath);
		}
		Integer secureWebsocketPort = brokerProperties.getSecureWebsocketPort();
		if (secureWebsocketPort != null) {
			String websocketPath = brokerProperties.getWebsocketPath();
			log.info(">>>>  MOQUETTE {} 启动成功, WebSocket-MQTT-SSL 端口：{} 地址：{}", Server.MOQUETTE_VERSION, websocketPort, websocketPath);
		}
	}
}
