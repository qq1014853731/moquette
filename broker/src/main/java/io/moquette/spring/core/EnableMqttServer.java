package io.moquette.spring.core;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用MQTT-Server
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/24 09:35
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(value = { MqttServerAutoConfiguration.class, MqttServerRunnerAutoConfiguration.class })
public @interface EnableMqttServer {

    boolean autoStart() default true;

}
