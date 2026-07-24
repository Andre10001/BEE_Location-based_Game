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
public class BindingXmlService extends AbstractXmlService {

    public BindingXmlService() {
        super(ExtendedElementTaskType.BINDING, SPACE_NS.getNamespaceUri());
    }

    @Override
    public TaskType getTypeKey() {
        return TaskType.BINDING;
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
        log.debug("[BindingXmlService] extractRaw on {} → {}",
                execution.getCurrentActivityId(), raw);
        return raw;
    }

    @Override
    public void patchInstanceValue(DelegateExecution execution, String newValue) {
        super.patchInstanceValue(execution, newValue);
        log.info("[BindingXmlService] patched <space:binding>='{}' on {}",
                newValue, execution.getCurrentActivityId());
    }

    @Override
    public void restoreInstanceValue(DelegateExecution execution, String rawValue) {
        super.restoreInstanceValue(execution, rawValue);
        log.info("[BindingXmlService] restored <space:binding>='{}' on {}",
                rawValue, execution.getCurrentActivityId());
    }
}