package org.unicam.intermediate.service.xml.bind;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.models.enums.ExtendedElementTaskType;
import org.unicam.intermediate.models.enums.TaskType;
import org.unicam.intermediate.service.xml.AbstractXmlService;

import static org.unicam.intermediate.utils.Constants.SPACE_NS;

@Slf4j
@Component
public class UnbindingXmlService extends AbstractXmlService {

    public UnbindingXmlService() {
        super(ExtendedElementTaskType.TYPE, SPACE_NS.getNamespaceUri()); // unbinding uses <space:type>unbinding</space:type>
    }

    @Override
    public TaskType getTypeKey() {
        return TaskType.UNBINDING;
    }

    @Override
    public String getNamespaceUri() {
        return namespaceUri;
    }

    @Override
    public ExtendedElementTaskType getLocalName() {
        return localName;
    }

    @Override
    public String extractRaw(DelegateExecution execution) {
        String raw = super.extractRaw(execution);
        log.debug("[UnbindingXmlService] extractRaw on {} → {}", execution.getCurrentActivityId(), raw);
        return raw;
    }
}