package org.unicam.intermediate.service.environmental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.models.pojo.View;
import org.unicam.intermediate.service.participant.ParticipantDataService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads and writes the environment files kept in the "envs" folder.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EnvironmentMapService {

    private final EnvironmentDataService environmentDataService;
    private final ParticipantDataService participantDataService;

    /**
     * Folder where the map files are stored.
     */
    @Value("${app.envs-dir:camunda-bpmn-engine/src/main/resources/envs}")
    private String envsDirectory;

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,60}$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns the names of the maps saved so far.
     */
    public List<String> listMapNames() throws IOException {
        Path directory = resolveDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                 .map(path -> path.getFileName().toString())
                 .filter(fileName -> fileName.endsWith(".json"))
                 .map(fileName -> fileName.substring(0, fileName.length() - ".json".length()))
                 .forEach(names::add);
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Reads one map by name.
     *
     * @return the content of the file, or null when no map has that name
     */
    public JsonNode readMap(String name) throws IOException {
        requireValidName(name);
        Path file = resolveDirectory().resolve(name + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        return objectMapper.readTree(Files.readAllBytes(file));
    }

    /**
     * Saves a map under the given name.
     *
     * @param name  the name of the map file
     * @param model the model produced by the editor
     */
    public void saveMap(String name, JsonNode model) throws IOException {
        requireValidName(name);
        if (model == null || !model.isObject()) {
            throw new IllegalArgumentException("The map must be a JSON object");
        }

        Path directory = resolveDirectory();
        Files.createDirectories(directory);
        Path file = directory.resolve(name + ".json");

        ObjectNode result = objectMapper.createObjectNode();
        if (Files.exists(file)) {
            JsonNode existing = objectMapper.readTree(Files.readAllBytes(file));
            if (existing.isObject()) {
                result = (ObjectNode) existing;
            }
        }
        result.setAll((ObjectNode) model);

        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(result);
        Files.writeString(file, prettyJson, StandardCharsets.UTF_8);

        log.info("[EnvironmentMapService] Map '{}' saved to {}", name, file.toAbsolutePath());
    }

    /**
     * Deploys the given map to the environment the engine is currently using.
     *
     * @param name the name of the map to deploy
     * @return a short description of what was loaded
     */
    public String deployMap(String name) throws IOException {
        requireValidName(name);
        Path file = resolveDirectory().resolve(name + ".json");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Map not found: " + name);
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);

        boolean environmentLoaded =
                environmentDataService.loadEnvironmentFromJsonContent(content, name);
        if (!environmentLoaded) {
            throw new IllegalArgumentException(
                    "The file does not contain a usable environment: " + name);
        }

        int participantCount =
                participantDataService.loadParticipantsFromJsonContent(content, name);

        int placeCount = environmentDataService.getPhysicalPlaces().size();
        log.info("[EnvironmentMapService] Map '{}' deployed: {} places, {} participants",
                name, placeCount, participantCount);

        if (participantCount == 0) {
            return String.format(
                    "Map '%s' deployed with %d places. It carries no participants, "
                    + "so the previous ones are still in use.", name, placeCount);
        }
        return String.format("Map '%s' deployed with %d places and %d participants.",
                name, placeCount, participantCount);
    }

    /**
     * Returns the ids of the physical places belonging to the given view.
     *
     * @param viewReference the id or the name of the view
     * @return the ids of the matching places, empty when the view is unknown
     */
    public List<String> getPlaceIdsInView(String viewReference) {
        if (viewReference == null || viewReference.isBlank()) {
            return List.of();
        }
 
        Optional<View> viewOpt = environmentDataService.getViews().stream()
                .filter(view -> viewReference.equals(view.getId())
                        || viewReference.equalsIgnoreCase(view.getName()))
                .findFirst();
 
        if (viewOpt.isEmpty()) {
            log.warn("[EnvironmentMapService] View not found: {}", viewReference);
            return List.of();
        }
 
        List<String> logicalPlaceIds = viewOpt.get().getLogicalPlaces();
        if (logicalPlaceIds == null || logicalPlaceIds.isEmpty()) {
            return List.of();
        }
 
        List<String> placeIds = new ArrayList<>();
        for (PhysicalPlace place : environmentDataService.getPhysicalPlaces()) {
            boolean belongs = logicalPlaceIds.stream().anyMatch(logicalPlaceId ->
                    environmentDataService.isPhysicalPlaceInLogicalPlace(place.getId(), logicalPlaceId));
            if (belongs) {
                placeIds.add(place.getId());
            }
        }
        return placeIds;
    }

    /** Turns the configured folder into a real path. */
    private Path resolveDirectory() {
        return Paths.get(envsDirectory);
    }

    /** Checks if the given name is valid for a map file. */
    private void requireValidName(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Invalid map name: only letters, digits, '-' and '_' are allowed");
        }
    }
}