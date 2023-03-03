### moquette with springboot

---
fork github: [moquette-io/moquette](https://github.com/moquette-io/moquette)  
add springboot support  
add redis support
---
#### 1. usage:
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.moquette.broker.config.ClasspathResourceLoader;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.ResourceLoaderConfig;
import io.moquette.spring.DynamicSslContextCreator;
import io.moquette.spring.core.MqttConfigurationCustomizer;
import io.moquette.spring.support.RedisRepositorySupport;
import io.moquette.spring.support.RepositorySupport;
import io.netty.handler.ssl.SslProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * @author admin
 * @version 1.0
 * @date 2023/3/3 11:40
 */
@Configuration
public class MqttBrokerConfiguration {

	/**
	 * load default config by classpath file
	 */
	@Bean
	public IConfig config() {
		ClasspathResourceLoader classpathResourceLoader = new ClasspathResourceLoader("moquette.conf");
		return new ResourceLoaderConfig(classpathResourceLoader);
	}

	/**
	 * autowire redis support
	 * @param redisConnectionFactory redis connection
	 * @param objectMapper mask default ObjectMapper
	 */
	@Bean
	public RepositorySupport repositorySupport(RedisConnectionFactory redisConnectionFactory,
											   ObjectMapper objectMapper) {
		RedisRepositorySupport redisRepositorySupport = new RedisRepositorySupport(redisConnectionFactory);
		redisRepositorySupport.setObjectMapper(objectMapper);
		return redisRepositorySupport;
	}

	/**
	 * dynamic modify config properties
	 */
    @Bean
    public MqttConfigurationCustomizer mqttConfigurationCustomizer() {
        return properties -> {
            properties.setPort(1883)
                .setPersistenceEnabled(Boolean.FALSE)
                .setSslProvider(SslProvider.OPENSSL)
                .setSslPort(8883)
                .setJksPath("certs/server.p12")
                .setKeyStoreType("pkcs12")
                .setKeyStorePassword("passw0rdsrv")
                .setKeyManagerPassword("passw0rdsrv")
                .setTelemetryEnabled(false)
                // add intercept handler
                .addInterceptHandler(new PublisherInterceptor());
        };
    }

	/**
	 * use dynamic ssl content
	 * @param config config
	 */
	@Bean
	public DynamicSslContextCreator dynamicSslContextCreator(IConfig config) {
		return new DynamicSslContextCreator(config);
	}
}
```

#### 2. dynamic load ssl cert
```java
import io.moquette.spring.DynamicSslContextCreator;
import io.moquette.spring.core.AdvancedTlsX509KeyManager;
import io.moquette.spring.core.AdvancedTlsX509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

@Component
@RequiredArgsConstructor
public class MyService {

	private final DynamicSslContextCreator dynamicSslContextCreator;


	@SneakyThrows
	@Override
	public void update() {

		// update keyManager's certs
		AdvancedTlsX509KeyManager keyManager = dynamicSslContextCreator.getKeyManager();
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyManager.updateIdentityCredentials(keyStore, "passw0rd".toCharArray());
		// or usage: keyManager.updateIdentityCredential(alias, privatekey, certs);

		// update trustManager's certs
		AdvancedTlsX509TrustManager trustManager = dynamicSslContextCreator.getTrustManager();
		KeyStore trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		trustManager.updateTrustCredentials(trustKeyStore);
		// or usage:
		// X509Certificate[] x509Certificates = new X509Certificate[0];
		// trustManager.updateTrustCredentials(x509Certificates);

	}
}
```
