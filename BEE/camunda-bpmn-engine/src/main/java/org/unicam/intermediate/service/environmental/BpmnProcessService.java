package org.unicam.intermediate.service.environmental;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads and deploys the BPMN process files kept in the "bpmn processes" folder.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BpmnProcessService {

    private final RepositoryService repositoryService;

    /** Folder where the BPMN files are stored. */
    @Value("${app.processes-dir:src/main/resources/bpmn-processes}")
    private String processesDirectory;

    /**
     * Returns the names of the BPMN files, without the ".bpmn" ending.
     *
     * @return the names in alphabetical order, empty when the folder does not exist
     */
    public List<String> listProcessNames() throws IOException {
        Path directory = Paths.get(processesDirectory);
        if (!Files.isDirectory(directory)) {
            log.warn("[BpmnProcessService] Folder not found: " + directory.toAbsolutePath());
            return List.of();
        }

        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                if (Files.isRegularFile(file) && fileName.toLowerCase().endsWith(".bpmn")) {
                    names.add(fileName.substring(0, fileName.length() - ".bpmn".length()));
                }
            }
        }

        Collections.sort(names);
        return names;
    }

    /**
     * Deploys the chosen BPMN file on the engine.
     *
     * @param name the name of the file, without the ".bpmn" ending
     * @return a short message about what was deployed
     */
    public String deployProcess(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Choose a BPMN process");
        }

        if (!listProcessNames().contains(name)) {
            throw new IllegalArgumentException("BPMN process not found: " + name);
        }

        Path file = Paths.get(processesDirectory).resolve(name + ".bpmn");

        try (InputStream content = Files.newInputStream(file)) {
            Deployment deployment = repositoryService.createDeployment()
                    .name(name)
                    .addInputStream(name + ".bpmn", content)
                    .deploy();

            long definitions = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .count();

            log.info("[BpmnProcessService] Process '" + name + "' deployed (" + definitions + " definitions)");
            return String.format("Process '%s' deployed with %d definition(s).", name, definitions);
        }
    }
}