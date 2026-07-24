package org.unicam.intermediate.service.xml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.models.enums.TaskType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mantiene tutti gli AbstractXmlService in mappa keyed by "namespace#typeKeyXmlValue".
 */
@Component
public class XmlServiceDispatcher {
    private final Map<String, AbstractXmlService> services;
    @Autowired
    public XmlServiceDispatcher(List<AbstractXmlService> svcList) {
        this.services = svcList.stream()
                .collect(Collectors.toMap(
                        s -> s.getNamespaceUri() + "#" + s.getTypeKey().toString(),
                        s -> s
                ));
    }
    public AbstractXmlService get(String namespaceUri, TaskType typeKeyXml) {
        return services.get(namespaceUri + "#" + typeKeyXml);
    }
}

