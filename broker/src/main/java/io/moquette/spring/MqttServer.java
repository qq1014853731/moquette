package io.moquette.spring;

import io.moquette.broker.ISslContextCreator;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.security.IAuthenticator;
import io.moquette.broker.security.IAuthorizatorPolicy;
import io.moquette.interception.InterceptHandler;

import java.io.IOException;
import java.util.List;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 18:00
 */
public class MqttServer extends Server {

    @Override
    public void startServer(IConfig config, List<? extends InterceptHandler> handlers, ISslContextCreator sslCtxCreator, IAuthenticator authenticator, IAuthorizatorPolicy authorizatorPolicy) throws IOException {

    }

}
