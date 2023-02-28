package io.moquette.spring.core;


import io.moquette.spring.BrokerProperties;

/**
 * 自定义mqtt配置
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/24 09:38
 */
@FunctionalInterface
public interface MqttConfigurationCustomizer {

	/**
	 * 自定义配置
	 * @param properties 原配置项，配置顺序为bean注入的顺序
	 */
	void customize(BrokerProperties properties);
}
