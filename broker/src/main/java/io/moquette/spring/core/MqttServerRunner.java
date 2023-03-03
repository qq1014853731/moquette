package io.moquette.spring.core;

import io.moquette.broker.ISslContextCreator;
import io.moquette.broker.Server;
import io.moquette.spring.BrokerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 18:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttServerRunner implements ApplicationRunner {

    private final BrokerProperties brokerProperties;
    private final Server server;
    private final ObjectProvider<ISslContextCreator> sslContextCreatorProvider;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        server.startServer(brokerProperties.parse2MemoryConfig(),
            brokerProperties.getInterceptHandlers(),
            sslContextCreatorProvider.getIfAvailable(), null, null);
        printSuccess(brokerProperties);
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
