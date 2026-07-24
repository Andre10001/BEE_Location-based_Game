package org.unicam.intermediate.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.activity.WaitStateActivity;
import org.unicam.intermediate.models.enums.TaskType;

import java.util.HashMap;
import java.util.Map;

import static org.unicam.intermediate.utils.Constants.*;

@Service
@Slf4j
@Getter
public class TaskTypeRegistry {

    private final Map<String, TaskTypeDefinition> taskTypes = new HashMap<>();
    private final ApplicationContext applicationContext;

    public TaskTypeRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        initializeDefaultTaskTypes();
    }

    private void initializeDefaultTaskTypes() {
        registerTaskType(TaskType.MOVEMENT, WaitStateActivity::new,movementExecutionListenerBeanName);
        registerTaskType(TaskType.BINDING, WaitStateActivity::new,bindingExecutionListenerBeanName);
        registerTaskType(TaskType.UNBINDING, WaitStateActivity::new,unbindingExecutionListenerBeanName);
        log.info("[TaskTypeRegistry] Initialized with {} task types", taskTypes.size());
    }

    public void registerTaskType(TaskType taskType,
                                 java.util.function.Supplier<AbstractBpmnActivityBehavior> behaviorSupplier,
                                 String listenerBeanName) {
        TaskTypeDefinition definition = new TaskTypeDefinition(
                 taskType, behaviorSupplier, listenerBeanName
        );
        taskTypes.put(taskType.toString(), definition);
        log.debug("[TaskTypeRegistry] Registered task type: {} with listener: {}", taskType.toString(), listenerBeanName);
    }

    public TaskTypeDefinition getTaskTypeDefinition(String typeValue) {
        return taskTypes.get(typeValue);
    }

    public boolean isRegisteredTaskType(String typeValue) {
        return taskTypes.containsKey(typeValue);
    }

    public class TaskTypeDefinition {
        private final TaskType taskType;
        private final java.util.function.Supplier<AbstractBpmnActivityBehavior> behaviorSupplier;
        private final String listenerBeanName;

        public TaskTypeDefinition(TaskType taskType,
                                  java.util.function.Supplier<AbstractBpmnActivityBehavior> behaviorSupplier,
                                  String listenerBeanName) {
            this.taskType = taskType;
            this.behaviorSupplier = behaviorSupplier;
            this.listenerBeanName = listenerBeanName;
        }

        public AbstractBpmnActivityBehavior createBehavior() { return behaviorSupplier.get(); }

        public ExecutionListener getListener() {
            try {
                return applicationContext.getBean(listenerBeanName, ExecutionListener.class);
            } catch (Exception e) {
                log.error("[TaskTypeRegistry] Failed to get listener bean '{}': {}", listenerBeanName, e.getMessage());
                return null;
            }
        }

    }
}