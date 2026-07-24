package org.unicam.intermediate.models.record;

import org.camunda.bpm.engine.runtime.Execution;

public record MovementTask(String executionId, String processInstanceId, String taskId, String destinationId, Execution execution){

}