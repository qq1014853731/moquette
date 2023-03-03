package io.moquette.spring.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/3/3 15:49
 */
@Slf4j
@Configuration
public class MqttServerRunnerAutoConfiguration implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        String annotationName = EnableMqttServer.class.getName();
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(annotationName, true));
        if (attributes != null) {
            boolean autoRun = attributes.getBoolean("autoStart");
            if (autoRun) {
                return new String[]{MqttServerRunner.class.getName()};
            }
        }
        log.debug("mqtt server is not auto run, places manual start");
        return new String[0];
    }
}
