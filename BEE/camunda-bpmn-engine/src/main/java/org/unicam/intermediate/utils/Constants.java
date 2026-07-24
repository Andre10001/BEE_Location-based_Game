package org.unicam.intermediate.utils;

import org.camunda.bpm.engine.impl.util.xml.Namespace;

public class Constants {

    public static final Namespace SPACE_NS = new Namespace("http://space" );

    public static final String movementExecutionListenerBeanName = "movementExecutionListener";
    public static final String unbindingExecutionListenerBeanName = "unbindingExecutionListener";
    public static final String bindingExecutionListenerBeanName = "bindingExecutionListener";
    public static final String environmentalExecutionListenerBeanName = "environmentalExecutionListener";
    public static final String exclusiveGatewayExecutionListenerBeanName = "exclusiveGatewayExecutionListener";
    public static final String genericTaskExecutionListenerBeanName = "genericTaskExecutionListener";
    public static final String discordanceCheckExecutionListenerBeanName = "discordanceCheckExecutionListener";
    public static final String messageThrowExecutionListenerBeanName = "messageThrowExecutionListener";

}
