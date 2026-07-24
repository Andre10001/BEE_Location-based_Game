package org.unicam.intermediate.config;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.spring.boot.starter.configuration.impl.AbstractCamundaConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.unicam.intermediate.listener.DynamicParseListener;

@Configuration
@EnableScheduling
@Slf4j
@AllArgsConstructor
public class CamundaConfig extends AbstractCamundaConfiguration {

    private final DynamicParseListener dynamicParseListener;

    @Bean
    public ProcessEnginePlugin processEnginePlugin() {
        return new AbstractProcessEnginePlugin() {
            @Override
            public void preInit(ProcessEngineConfigurationImpl configuration) {
                configuration.getCustomPreBPMNParseListeners().add(dynamicParseListener);
                log.info("[CamundaConfig] Registered parse listeners");
            }
        };
    }
}