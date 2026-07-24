package org.unicam.intermediate.service.participant;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.config.ParticipantConfiguration;
import org.unicam.intermediate.events.ParticipantPositionChangedEvent;
import org.unicam.intermediate.models.pojo.Participant;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Service for managing participant data independently from environment.json.
 * Currently loads static participant data at initialization.
 */
@Service
@Slf4j
@Getter
@Scope("singleton")
public class ParticipantDataService {

    private final ApplicationEventPublisher eventPublisher;
    private final ResourceLoader resourceLoader;

    @Value("${app.scenario:}")
    private String participantScenario;

    public ParticipantDataService(ApplicationEventPublisher eventPublisher, ResourceLoader resourceLoader) {
        this.eventPublisher = eventPublisher;
        this.resourceLoader = resourceLoader;
    }

    // Store participants in a concurrent map for thread-safe updates
    private final Map<String, Participant> participantsMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        loadParticipantsFromConfiguration();
    }

    /**
     * Loads participants from scenario-specific environment file.
     * Based on app.scenario property (university, farm, emergency, city).
     * Falls back to creating empty map if file not found.
     */
    private void loadParticipantsFromConfiguration() {
        participantsMap.clear();

        String scenario = participantScenario != null ? participantScenario.trim().toLowerCase() : "";
        if (scenario.isBlank()) {
            log.info("[ParticipantDataService] app.scenario is empty, skipping participants configuration loading");
            log.info("[ParticipantDataService] Total participants in map after loading: {}", participantsMap.size());
            return;
        }

        try {
            // Determine the filename based on the scenario
            String filename = String.format("classpath:envs/%s.json", scenario);
            
            log.info("[ParticipantDataService] Attempting to load participants from scenario: {}", scenario);
            log.info("[ParticipantDataService] Resource filename: {}", filename);
            
            Resource resource = resourceLoader.getResource(filename);
            log.info("[ParticipantDataService] Resource exists: {}", resource.exists());
            log.info("[ParticipantDataService] Resource URL: {}", resource.getURL());
            
            if (resource.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                ParticipantConfiguration config = mapper.readValue(resource.getInputStream(), ParticipantConfiguration.class);
                
                log.info("[ParticipantDataService] ParticipantConfiguration loaded. Participants count: {}", 
                    config.getParticipants() != null ? config.getParticipants().size() : 0);
                
                if (config.getParticipants() != null) {
                    List<Participant> participants = config.toParticipants();
                    for (Participant participant : participants) {
                        if (participant != null && participant.getId() != null) {
                            participantsMap.put(participant.getId(), participant);
                            log.debug("[ParticipantDataService] Added participant: {} - {}", 
                                participant.getId(), participant.getName());
                        }
                    }
                    log.info("[ParticipantDataService] Successfully loaded {} participants from scenario '{}' (file: {})", 
                        participants.size(), scenario, filename);
                } else {
                    log.warn("[ParticipantDataService] ParticipantConfiguration has null participants list");
                }
            } else {
                log.warn("[ParticipantDataService] Configuration file '{}' for scenario '{}' NOT FOUND", 
                    filename, scenario);
                log.warn("[ParticipantDataService] Available resources should include: envs/university.json, envs/farm.json, envs/emergency.json, envs/city.json");
            }
        } catch (Exception e) {
            log.error("[ParticipantDataService] Error loading participants from configuration: {}", e.getMessage(), e);
        }
        
        log.info("[ParticipantDataService] Total participants in map after loading: {}", participantsMap.size());
    }

    /**
     * Get all participants as a list.
     */
    public List<Participant> getParticipants() {
        return new ArrayList<>(participantsMap.values());
    }

    /**
     * Get a specific participant by ID.
     */
    public Optional<Participant> getParticipant(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(participantsMap.get(id));
    }

    /**
     * Get a participant by name (case-insensitive).
     */
    public Optional<Participant> getParticipantByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return participantsMap.values().stream()
                .filter(participant -> participant.getName() != null
                        && participant.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    /**
     * Update a participant's position.
     */
    public void updateParticipantPosition(String id, String position) {
        if (id == null) {
            return;
        }
        Participant participant = participantsMap.get(id);
        if (participant != null) {
            String oldPosition = participant.getPosition();
            participant.setPosition(position);
            log.debug("[ParticipantDataService] Updated participant {} position: {} -> {}", id, oldPosition, position);
            eventPublisher.publishEvent(new ParticipantPositionChangedEvent(id, oldPosition, position));
        } else {
            log.warn("[ParticipantDataService] Participant {} not found, cannot update position", id);
        }
    }

    /**
     * Add a new participant dynamically (if needed in the future).
     */
    public void addParticipant(Participant participant) {
        if (participant != null && participant.getId() != null) {
            participantsMap.put(participant.getId(), participant);
            log.info("[ParticipantDataService] Added new participant: {}", participant.getId());
        }
    }

    /**
     * Remove a participant dynamically (if needed in the future).
     */
    public void removeParticipant(String id) {
        if (id != null) {
            Participant removed = participantsMap.remove(id);
            if (removed != null) {
                log.info("[ParticipantDataService] Removed participant: {}", id);
            }
        }
    }

    /**
     * Check if any participants are loaded.
     */
    public boolean isLoaded() {
        return !participantsMap.isEmpty();
    }

    /**
     * Loads participants from a raw JSON payload (e.g., attached during deployment).
     * Returns the number of loaded participants; returns 0 when no participants section is present.
     */
    public synchronized int loadParticipantsFromJsonContent(String jsonContent, String source) {
        if (jsonContent == null || jsonContent.isBlank()) {
            return 0;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonContent);

            if (!root.has("participants")) {
                return 0;
            }

            ParticipantConfiguration config = mapper.readValue(jsonContent, ParticipantConfiguration.class);
            List<Participant> participants = config != null ? config.toParticipants() : List.of();

            participantsMap.clear();
            for (Participant participant : participants) {
                if (participant != null && participant.getId() != null) {
                    participantsMap.put(participant.getId(), participant);
                }
            }

            log.info("[ParticipantDataService] Loaded {} participants from deployment JSON '{}'",
                    participantsMap.size(), source);
            return participantsMap.size();
        } catch (Exception e) {
            log.warn("[ParticipantDataService] Could not apply deployment JSON '{}' as participants: {}",
                    source, e.getMessage());
            return 0;
        }
    }

    /**
     * Replaces current participants with the given list (typically extracted from BPMN collaboration participants).
     * Participants with null/blank IDs are ignored.
     */
    public synchronized int replaceParticipants(List<Participant> participants, String source) {
        participantsMap.clear();

        if (participants == null || participants.isEmpty()) {
            log.info("[ParticipantDataService] Cleared participants from source '{}' (no participants provided)", source);
            return 0;
        }

        for (Participant participant : participants) {
            if (participant == null || participant.getId() == null || participant.getId().isBlank()) {
                continue;
            }
            participantsMap.put(participant.getId(), participant);
        }

        log.info("[ParticipantDataService] Replaced participants from source '{}' -> total {}",
                source, participantsMap.size());
        return participantsMap.size();
    }

    /**
     * Merges participants into the current set without overwriting existing ones.
     * Only participants with new IDs are added.
     *
     * @return number of newly added participants
     */
    public synchronized int mergeParticipants(List<Participant> participants, String source) {
        if (participants == null || participants.isEmpty()) {
            log.info("[ParticipantDataService] No participants to merge from source '{}'", source);
            return 0;
        }

        int added = 0;
        for (Participant participant : participants) {
            if (participant == null || participant.getId() == null || participant.getId().isBlank()) {
                continue;
            }

            Participant existing = participantsMap.putIfAbsent(participant.getId(), participant);
            if (existing == null) {
                added++;
            }
        }

        log.info("[ParticipantDataService] Merged participants from source '{}' -> added {}, total {}",
                source, added, participantsMap.size());
        return added;
    }
}
