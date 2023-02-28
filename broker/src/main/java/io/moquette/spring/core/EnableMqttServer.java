package io.moquette.spring.core;

import cn.hutool.extra.spring.EnableSpringUtil;
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
@Import(value = MqttServerAutoConfiguration.class)
@EnableSpringUtil
public @interface EnableMqttServer {

    RepositoryType repositoryType() default RepositoryType.MEMORY;

}
