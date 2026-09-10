package it.unicam.locationbasedgame.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unicam.locationbasedgame.config.BeeClient;
import it.unicam.locationbasedgame.dto.OutpostDTO;
import it.unicam.locationbasedgame.enums.OutpostState;
import it.unicam.locationbasedgame.model.Outpost;
import it.unicam.locationbasedgame.model.Topic;
import it.unicam.locationbasedgame.repository.OutpostRepository;
import it.unicam.locationbasedgame.repository.TopicRepository;
import it.unicam.locationbasedgame.service.interfaces.ICaptureService;
import it.unicam.locationbasedgame.service.interfaces.IOutpostService;
import it.unicam.locationbasedgame.service.interfaces.IProcessTimerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of IOutpostService, supported by OutpostRepository.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutpostService implements IOutpostService {

    private static final String OUTPOST_VIEW = "Outposts";

    private static final int MIN_DIFFICULTY = 1;
    private static final int MAX_DIFFICULTY = 5;

    private final OutpostRepository outpostRepository;
    private final TopicRepository topicRepository;
    private final BeeClient beeClient;
    private final IProcessTimerService processTimerService;
    private final ICaptureService captureService;

    @Override
    @Transactional
    public OutpostDTO assignTopics(String placeId, OutpostDTO outpostDTO) {
        validate(placeId, outpostDTO);

        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseGet(() -> {
                    Outpost created = new Outpost();
                    created.setPlaceId(placeId);
                    return created;
                });

        configure(outpost, outpostDTO.getPlaceName(), outpostDTO.getDifficulty(),
                outpostDTO.getRequiredPlayers(), outpostDTO.getMaxTopics());

        List<Topic> topics = new ArrayList<>();
        for (Long topicId : outpostDTO.getTopicIds()) {
            Topic topic = topicRepository.findById(topicId)
                    .orElseThrow(() -> new EntityNotFoundException("Topic not found with id " + topicId));
            if (!topics.contains(topic)) {
                topics.add(topic);
            }
        }
        outpost.setTopics(topics);

        return toDto(outpostRepository.save(outpost));
    }

    @Override
    public OutpostDTO getOutpostByPlaceId(String placeId) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
        return toDto(outpost);
    }

    @Override
    public List<OutpostDTO> getAllOutposts() {
        return outpostRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void resetAttempt(String placeId) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
 
        if (Outpost.ATTEMPT_PENDING.equals(outpost.getLastAttempt())) {
            return;
        }
        outpost.resetAttempt();
        outpostRepository.save(outpost);
        syncToBee(outpost);
    }

    @Override
    @Transactional
    public List<OutpostDTO> syncWithEnvironment() {
        processTimerService.forget();
        captureService.clear();

        List<BeeClient.BeePlace> places = beeClient.getPlacesInView(OUTPOST_VIEW);
        if (places.isEmpty()) {
            throw new IllegalStateException(
                    "The deployed map has no place in the " + OUTPOST_VIEW + " view");
        }
 
        int created = 0;
        for (BeeClient.BeePlace place : places) {
            Outpost outpost = outpostRepository.findByPlaceId(place.getId()).orElse(null);
            if (outpost == null) {
                outpost = new Outpost();
                outpost.setPlaceId(place.getId());
                outpost.setTopics(new ArrayList<>());
                created++;
            }
 
            configure(outpost, place.getName(), place.getInt("difficulty", 1),
                    place.getInt("requiredPlayers", 1), place.getInt("maxTopics", 1));
            outpost.setState(parseState(place.getText("status", "neutral")));
            outpost.endAttempt(false);
 
            Outpost saved = outpostRepository.save(outpost);
            syncToBee(saved);
        }

        log.info("[OutpostService] Synchronised " + places.size() + " place(s) from the map, " + created + " new");
 
        return getAllOutposts();
    }

    @Override
    public void deleteOutpost(String placeId) {
                Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
        outpostRepository.delete(outpost);
    }

    /** Synchronize outpost data between the application and BEE. */
    private void syncToBee(Outpost outpost) {
        String placeId = outpost.getPlaceId();
        boolean captured = outpost.getState() != OutpostState.neutral;
 
        beeClient.updatePlaceAttribute(placeId, "status", outpost.getState().name());
        beeClient.updatePlaceAttribute(placeId, "isCaptured", String.valueOf(captured));
        beeClient.updatePlaceAttribute(placeId, "isBeingCaptured",
                String.valueOf(outpost.isBeingCaptured()));
        beeClient.updatePlaceAttribute(placeId, "lastAttempt", outpost.getLastAttempt());
        beeClient.updatePlaceAttribute(placeId, "difficulty",
                String.valueOf(outpost.getDifficulty()));
        beeClient.updatePlaceAttribute(placeId, "requiredPlayers",
                String.valueOf(outpost.getRequiredPlayers()));
        beeClient.updatePlaceAttribute(placeId, "maxTopics",
                String.valueOf(outpost.getMaxTopics()));
    }

    /**
     * Configures an outpost with the given data.
     *
     * @param outpost the outpost to configure
     * @param placeName the display name of its place
     * @param difficulty the difficulty of the questions it asks
     * @param requiredPlayers how many players it takes to conquer it
     * @param maxTopics how many topics it accepts
     */
    private void configure(Outpost outpost, String placeName, int difficulty,
                           int requiredPlayers, int maxTopics) {
        if (placeName == null || placeName.isBlank()) {
            throw new IllegalArgumentException("placeName must not be empty");
        }
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty of " + placeName + " must be between "
                    + MIN_DIFFICULTY + " and " + MAX_DIFFICULTY + ", not " + difficulty);
        }
        if (requiredPlayers < 1) {
            throw new IllegalArgumentException(
                    "requiredPlayers of " + placeName + " must be at least 1");
        }
        if (maxTopics < 1) {
            throw new IllegalArgumentException(
                    "maxTopics of " + placeName + " must be at least 1");
        }
 
        outpost.setPlaceName(placeName.trim());
        outpost.setDifficulty(difficulty);
        outpost.setRequiredPlayers(requiredPlayers);
        outpost.setMaxTopics(maxTopics);
    }

    /** Reads a status written in the map, or sets it to neutral otherwise. */
    private OutpostState parseState(String status) {
        try {
            return OutpostState.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("[OutpostService] Unknown status '" + status + "' in the map, using neutral");
            return OutpostState.neutral;
        }
    }

    /** Checks the input data before saving an outpost. */
    private void validate(String placeId, OutpostDTO dto) {
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId must not be empty");
        }
        if (dto == null || dto.getTopicIds() == null) {
            throw new IllegalArgumentException("topicIds must not be null");
        }
        if (dto.getTopicIds().size() > dto.getMaxTopics()) {
            throw new IllegalArgumentException(
                    "This outpost accepts at most " + dto.getMaxTopics() + " topics");
        }
    }

    /** Converts an Outpost entity into its DTO representation. */
    private OutpostDTO toDto(Outpost outpost) {
        List<Long> topicIds = outpost.getTopics().stream()
                .map(Topic::getId)
                .collect(Collectors.toList());
        List<String> topicNames = outpost.getTopics().stream()
                .map(Topic::getName)
                .collect(Collectors.toList());
       return new OutpostDTO(outpost.getId(), outpost.getPlaceId(), outpost.getPlaceName(),
                outpost.getDifficulty(), outpost.getRequiredPlayers(), outpost.getMaxTopics(),
                topicIds, topicNames, outpost.getState());
    }
}