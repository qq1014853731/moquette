package io.moquette.spring.core;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.system.SystemUtil;
import io.moquette.broker.Server;
import io.moquette.broker.config.ClasspathResourceLoader;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.ResourceLoaderConfig;
import io.moquette.spring.BrokerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/24 09:33
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
public class MqttServerAutoConfiguration implements ApplicationContextAware {

    public static ApplicationContext applicationContext;

	/**
	 * 系统配置环境变量前缀，后缀为配置文件的key，可以动态配置
	 */
	private static final String SYSTEM_ENV_PREFIX = "mqtt.";


	/**
	 * @return 默认配置文件配置
	 */
	@Bean
	@ConditionalOnMissingBean(IConfig.class)
	public IConfig config() {
		ClasspathResourceLoader classpathResourceLoader = new ClasspathResourceLoader(IConfig.DEFAULT_CONFIG);
		return new ResourceLoaderConfig(classpathResourceLoader);
	}

	/**
	 * @param config 原有配置文件配置
	 * @param mqttConfigurationCustomizers 自定义配置方法
	 * @return 自定义配置
	 */
	@Bean
	@ConditionalOnBean(value = {IConfig.class})
	public BrokerProperties brokerProperties(IConfig config, List<MqttConfigurationCustomizer> mqttConfigurationCustomizers) {
		// config转properties
		Properties properties = config.getProperties();

		BrokerProperties brokerProperties = new BrokerProperties();
		Collection<String> values = BrokerProperties.FIELD_NAME_CACHE_MAP.values();
		for (String configKey : values) {
            Object value = SystemUtil.get(SYSTEM_ENV_PREFIX + configKey);
            if (value == null) {
                value = properties.get(configKey);
            }
            if (value == null) {
                continue;
            }

			String configValue = String.valueOf(value);
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
	 * @param sslContextCreatorProvider sslContext创建器
	 * @return 实例Bean
	 */
	@Bean
	@ConditionalOnMissingBean(Server.class)
	@ConditionalOnBean(BrokerProperties.class)
	public Server server() throws IOException {
		return new Server();
	}

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        MqttServerAutoConfiguration.applicationContext = applicationContext;
    }
}
